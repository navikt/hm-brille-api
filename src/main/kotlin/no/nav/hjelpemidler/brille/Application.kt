package no.nav.hjelpemidler.brille

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
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
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.IgnoreTrailingSlash
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.runBlocking
import no.nav.hjelpemidler.brille.HttpClientConfig.httpClient
import no.nav.hjelpemidler.brille.azuread.AzureAdClient
import no.nav.hjelpemidler.brille.db.DatabaseConfiguration
import no.nav.hjelpemidler.brille.db.VedtakStorePostgres
import no.nav.hjelpemidler.brille.enhetsregisteret.EnhetsregisteretClient
import no.nav.hjelpemidler.brille.enhetsregisteret.Organisasjonsnummer
import no.nav.hjelpemidler.brille.exceptions.SjekkOptikerPluginException
import no.nav.hjelpemidler.brille.exceptions.configureStatusPages
import no.nav.hjelpemidler.brille.internal.selfTestRoutes
import no.nav.hjelpemidler.brille.internal.setupMetrics
import no.nav.hjelpemidler.brille.model.AvvisningsType
import no.nav.hjelpemidler.brille.pdl.PdlClient
import no.nav.hjelpemidler.brille.pdl.PdlService
import no.nav.hjelpemidler.brille.syfohelsenettproxy.SyfohelsenettproxyClient
import no.nav.hjelpemidler.brille.vilkarsvurdering.Vilkårsvurdering
import org.slf4j.event.Level
import java.time.LocalDate
import java.util.TimeZone

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

    installAuthentication(httpClient())

    routing {
        selfTestRoutes()

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

                    // TODO: Journalfør søknad/vedtak som dokument i joark på barnet
                    // TODO: Varsle foreldre/verge (ikke i kode 6/7 saker) om vedtaket

                    call.respond(HttpStatusCode.Created, "201 Created")
                }
            }
        }

        post("/temp/test/helsenett") {
            data class Request(val fnr: String)

            val req = call.receive<Request>()

            val behandler =
                runCatching { runBlocking { syfohelsenettproxyClient.hentBehandler(req.fnr) } }.getOrElse {
                    throw SjekkOptikerPluginException(
                        HttpStatusCode.InternalServerError,
                        "Kunne ikke hente data fra syfohelsenettproxyClient: $it",
                        it
                    )
                }

            call.respond(jsonMapper.writeValueAsString(behandler))
        }

        post("/temp/test/medlemskap") {
            data class Request(val fnr: String)

            val req = call.receive<Request>()
            val now = LocalDate.now()

            data class MedlemskapPeriode(val fom: LocalDate, val tom: LocalDate)
            data class MedlemskapBrukerInput(val arbeidUtenforNorge: Boolean)
            data class MedlemskapRequest(
                val fnr: String,
                val førsteDagForYtelse: LocalDate,
                val periode: MedlemskapPeriode,
                val brukerinput: MedlemskapBrukerInput,
            )

            val client = httpHelper(azureAdClient)
            val response =
                client.post(if (Configuration.profile == Profile.DEV) "https://medlemskap-oppslag.dev.nav.no/" else "https://medlemskap-oppslag.intern.nav.no") {
                    contentType(ContentType.Application.Json)
                    setBody(
                        MedlemskapRequest(
                            fnr = req.fnr,
                            førsteDagForYtelse = now,
                            periode = MedlemskapPeriode(fom = now, tom = now),
                            brukerinput = MedlemskapBrukerInput(arbeidUtenforNorge = false),
                        )
                    )
                }

            val body = response.bodyAsText()
            call.respond(body)
        }
    }
}

fun httpHelper(azureAdClient: AzureAdClient): HttpClient {
    return HttpClient(CIO.create()) {
        expectSuccess = true
        install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
            jackson {
                registerModule(JavaTimeModule())
                disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            }
        }
        install(Auth) {
            bearer {
                loadTokens {
                    azureAdClient.getToken(if (Configuration.profile == Profile.DEV) "api://dev-gcp.medlemskap.medlemskap-oppslag/.default" else "api://prod-gcp.medlemskap.medlemskap-oppslag/.default")
                        .toBearerTokens()
                }
                refreshTokens { null }
                sendWithoutRequest { true }
            }
        }
    }
}
