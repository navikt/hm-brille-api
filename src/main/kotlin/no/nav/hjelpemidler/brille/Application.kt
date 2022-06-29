package no.nav.hjelpemidler.brille

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.auth.authenticate
import io.ktor.server.plugins.callid.callIdMdc
import io.ktor.server.plugins.callloging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.path
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.IgnoreTrailingSlash
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import mu.KotlinLogging
import no.nav.hjelpemidler.brille.HttpClientConfig.httpClient
import no.nav.hjelpemidler.brille.azuread.AzureAdClient
import no.nav.hjelpemidler.brille.db.DatabaseConfiguration
import no.nav.hjelpemidler.brille.db.VedtakStorePostgres
import no.nav.hjelpemidler.brille.enhetsregisteret.EnhetsregisteretClient
import no.nav.hjelpemidler.brille.enhetsregisteret.Organisasjonsnummer
import no.nav.hjelpemidler.brille.exceptions.configureStatusPages
import no.nav.hjelpemidler.brille.internal.selfTestRoutes
import no.nav.hjelpemidler.brille.internal.setupMetrics
import no.nav.hjelpemidler.brille.kafka.AivenKafkaConfiguration
import no.nav.hjelpemidler.brille.kafka.KafkaProducer
import no.nav.hjelpemidler.brille.model.AvvisningsType
import no.nav.hjelpemidler.brille.pdl.PdlClient
import no.nav.hjelpemidler.brille.pdl.PdlService
import no.nav.hjelpemidler.brille.syfohelsenettproxy.SyfohelsenettproxyClient
import no.nav.hjelpemidler.brille.vilkarsvurdering.Vilkårsvurdering
import org.slf4j.event.Level
import java.util.TimeZone
import java.util.UUID

private val log = KotlinLogging.logger {}

fun main(args: Array<String>): Unit = io.ktor.server.cio.EngineMain.main(args)

fun Application.module() {
    configure()
    setupRoutes()
}

// Config stuff that we want to reuse in tests
fun Application.configure() {
    TimeZone.setDefault(TimeZone.getTimeZone("Europe/Oslo"))

    setupCallId()
    setupMetrics()
    configureStatusPages()

    install(ContentNegotiation) {
        jackson {
            registerModule(JavaTimeModule())
            disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        }
    }
    install(CallLogging) {
        level = Level.TRACE
        filter { call ->
            !call.request.path().startsWith("/hm/internal")
        }
        // Set correlation-id i logginnslag. Også tilgjengelig direkte med: MDC.get(MDC_CORRELATION_ID)
        callIdMdc(MDC_CORRELATION_ID)
    }
    install(IgnoreTrailingSlash)
}

// Wire up services and routes
fun Application.setupRoutes() {
    val azureAdClient = AzureAdClient()
    val pdlService = PdlService(
        PdlClient(
            Configuration.pdlProperties.graphqlUri,
            Configuration.pdlProperties.apiScope,
            azureAdClient,
        )
    )

    val dataSource = DatabaseConfiguration(Configuration.dbProperties).dataSource()
    val vedtakStore = VedtakStorePostgres(dataSource)
    val enhetsregisteretClient = EnhetsregisteretClient(Configuration.enhetsregisteretProperties.baseUrl)
    val syfohelsenettproxyClient = SyfohelsenettproxyClient(
        Configuration.syfohelsenettproxyProperties.baseUrl,
        Configuration.syfohelsenettproxyProperties.scope,
        azureAdClient,
    )
    val vilkårsvurdering = Vilkårsvurdering(vedtakStore)
    val kafkaProducer = KafkaProducer(AivenKafkaConfiguration().aivenKafkaProducer())

    installAuthentication(httpClient())

    routing {
        selfTestRoutes()

        // TODO: erstatt /sok når ferdig
        post("/sok_test") {
            data class Request(
                val fnr: String,
                val orgnr: String,
            )

            val request = call.receive<Request>()
            if (request.fnr.count() != 11) error("Fnr er ikke gyldig (må være 11 siffre)")

            /*
            val personInformasjon = pdlService.hentPersonDetaljer(request.fnr)
            log.info { "personInformasjon <$personInformasjon>" }


            // Valider vilkår for å forsikre oss om at alle sjekker er gjort
            val vilkår = vilkårsvurdering.kanSøke(personInformasjon)
            log.info { "vilkår <$vilkår>" }
            if (!vilkår.valider()) {
                call.respond(HttpStatusCode.BadRequest, "{}")
                return@post
            }
            */

            // Innvilg søknad og opprett vedtak
            vedtakStore.opprettVedtak(
                request.fnr,
                "15084300133", // <- TODO: SEDAT hardkodet for dev //call.extractFnr(),
                request.orgnr,
                jsonMapper.valueToTree(request)
            )

            val antallRader = vedtakStore.tellRader() // TODO: vi må finne ut hvordan vi faktisk sender sakId. Skal vi heller legge inn en sakId-kolonne som autoinkrementer?

            // Journalfør søknad/vedtak som dokument i joark på barnet
            val barneBrilleVedtakData = KafkaProducer.BarnebrilleVedtakData(
                fnr = request.fnr,
                orgnr = request.orgnr,
                eventId = UUID.randomUUID(),
                "hm-barnebrillevedtak-opprettet",
                navnAvsender = "Ole Brumm", // TODO: hvilket navn skal dette egentlig være? Navnet til bruker (barn) eller optiker?
                sakId = (antallRader + 1).toString()
            )
            val event = jsonMapper.writeValueAsString(barneBrilleVedtakData)
            kafkaProducer.produceEvent(request.fnr, event)

            // TODO: Varsle foreldre/verge (ikke i kode 6/7 saker) om vedtaket

            call.respond(HttpStatusCode.Created, "201 Created")
        }

        authenticate(TOKEN_X_AUTH) {
            authenticateOptiker(syfohelsenettproxyClient) {
                post("/sjekk-kan-soke") {
                    data class Request(val fnr: String)
                    data class Response(
                        val fnr: String,
                        val navn: String,
                        val alder: Int,
                        val kanSøke: Boolean,
                        val begrunnelse: List<AvvisningsType>,
                    )

                    val fnrBruker = call.receive<Request>().fnr
                    if (fnrBruker.count() != 11) error("Fnr er ikke gyldig (må være 11 siffre)")

                    val personInformasjon = pdlService.hentPersonDetaljer(fnrBruker)

                    val vilkår = vilkårsvurdering.kanSøke(personInformasjon)

                    call.respond(
                        Response(
                            fnrBruker,
                            "${personInformasjon.fornavn} ${personInformasjon.etternavn}",
                            personInformasjon.alder!!,
                            vilkår.valider(),
                            vilkår.avvisningsGrunner(),
                        )
                    )
                }

                get("/orgnr") {
                    val fnrOptiker = call.extractFnr()
                    call.respond(vedtakStore.hentTidligereBrukteOrgnrForOptikker(fnrOptiker))
                }

                get("/enhetsregisteret/enheter/{organisasjonsnummer}") {
                    val organisasjonsnummer =
                        call.parameters["organisasjonsnummer"] ?: error("Mangler organisasjonsnummer i url")
                    val organisasjonsenhet =
                        enhetsregisteretClient.hentOrganisasjonsenhet(Organisasjonsnummer(organisasjonsnummer))
                    call.respond(organisasjonsenhet)
                }

                post("/sok") {
                    data class Request(
                        val fnr: String,
                        val orgnr: String,
                    )

                    val request = call.receive<Request>()

                    if (request.fnr.count() != 11) error("Fnr er ikke gyldig (må være 11 siffre)")

                    val personInformasjon = pdlService.hentPersonDetaljer(request.fnr)

                    // Valider vilkår for å forsikre oss om at alle sjekker er gjort
                    val vilkår = vilkårsvurdering.kanSøke(personInformasjon)
                    if (!vilkår.valider()) {
                        call.respond(HttpStatusCode.BadRequest, "{}")
                        return@post
                    }

                    // Innvilg søknad og opprett vedtak
                    vedtakStore.opprettVedtak(
                        request.fnr,
                        call.extractFnr(),
                        request.orgnr,
                        jsonMapper.valueToTree(request)
                    )

                    // TODO: Journalfør søknad/vedtak som dokument i joark på barnet (se /sok_test)
                    // TODO: Varsle foreldre/verge (ikke i kode 6/7 saker) om vedtaket

                    call.respond(HttpStatusCode.Created, "201 Created")
                }
            }
        }
    }
}
