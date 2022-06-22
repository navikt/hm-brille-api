package no.nav.hjelpemidler.brille

import com.fasterxml.jackson.databind.DeserializationFeature
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
import no.nav.hjelpemidler.brille.azuread.AzureAdClient
import no.nav.hjelpemidler.brille.configurations.applicationConfig.HttpClientConfig.httpClient
import no.nav.hjelpemidler.brille.configurations.applicationConfig.MDC_CORRELATION_ID
import no.nav.hjelpemidler.brille.configurations.applicationConfig.setupCallId
import no.nav.hjelpemidler.brille.db.DatabaseConfig
import no.nav.hjelpemidler.brille.db.VedtakStorePostgres
import no.nav.hjelpemidler.brille.enhetsregisteret.EnhetsregisteretClient
import no.nav.hjelpemidler.brille.enhetsregisteret.Organisasjonsnummer
import no.nav.hjelpemidler.brille.exceptions.configureStatusPages
import no.nav.hjelpemidler.brille.internal.selvtestRoutes
import no.nav.hjelpemidler.brille.internal.setupMetrics
import no.nav.hjelpemidler.brille.model.AvvisningsType
import no.nav.hjelpemidler.brille.pdl.client.PdlClient
import no.nav.hjelpemidler.brille.pdl.service.PdlService
import no.nav.hjelpemidler.brille.utils.Vilkårsvurdering
import no.nav.hjelpemidler.brille.wiremock.WiremockConfig
import org.slf4j.event.Level
import java.util.TimeZone

fun main(args: Array<String>): Unit = io.ktor.server.netty.EngineMain.main(args)

fun Application.module() {
    configure()
    setupRoutes()
}

// Config stuff that we want to reuse in tests
fun Application.configure() {
    TimeZone.setDefault(TimeZone.getTimeZone("Europe/Oslo"))

    install(ContentNegotiation) {
        jackson {
            disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        }
    }
    setupCallId()
    configureStatusPages()

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
    val httpClient = httpClient()
    val pdlService = PdlService(PdlClient(azureAdClient, httpClient))

    val dataSource = DatabaseConfig(Configuration.dbProperties).dataSource()
    val vedtakStore = VedtakStorePostgres(dataSource)
    val enhetsregisteretClient = EnhetsregisteretClient(Configuration.enhetsregisteretProperties.baseUrl)
    val vilkårsvurdering = Vilkårsvurdering(vedtakStore)

    installAuthentication(httpClient)

    routing {
        selvtestRoutes()

        authenticate(TOKEN_X_AUTH) {
            post("/sjekk-kan-søke") {
                data class Request(val fnr: String)
                data class Response(
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
                        "${personInformasjon.fornavn} ${personInformasjon.etternavn}",
                        personInformasjon.alder!!,
                        vilkår.valider(),
                        vilkår.avvisningsGrunner(),
                    )
                )
            }

            get("/enhetsregisteret/enheter/{organisasjonsnummer}") {
                val organisasjonsnummer =
                    call.parameters["organisasjonsnummer"] ?: error("Mangler organisasjonsnummer i url")
                val organisasjonsenhet =
                    enhetsregisteretClient.hentOrganisasjonsenhet(Organisasjonsnummer(organisasjonsnummer))
                call.respond(organisasjonsenhet)
            }
        }
    }

    setupMetrics()

    if (Configuration.profile == Profile.LOCAL) {
        WiremockConfig().wiremockServer()
    }
}
