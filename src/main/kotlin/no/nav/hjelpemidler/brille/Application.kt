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
import io.ktor.server.response.respond
import io.ktor.server.routing.IgnoreTrailingSlash
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import no.nav.hjelpemidler.brille.azuread.AzureAdClient
import no.nav.hjelpemidler.brille.configurations.applicationConfig.HttpClientConfig.httpClient
import no.nav.hjelpemidler.brille.configurations.applicationConfig.MDC_CORRELATION_ID
import no.nav.hjelpemidler.brille.configurations.applicationConfig.setupCallId
import no.nav.hjelpemidler.brille.db.DatabaseConfig
import no.nav.hjelpemidler.brille.db.VedtakStorePostgres
import no.nav.hjelpemidler.brille.exceptions.configureStatusPages
import no.nav.hjelpemidler.brille.internal.selvtestRoutes
import no.nav.hjelpemidler.brille.internal.setupMetrics
import no.nav.hjelpemidler.brille.pdl.client.PdlClient
import no.nav.hjelpemidler.brille.pdl.service.PdlService
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

    installAuthentication(httpClient)

    routing {
        selvtestRoutes()

        get("/sjekk-kan-søke/{fnrBruker}") {
            val fnrBruker = call.parameters["fnrBruker"] ?: error("Mangler fnr som skal sjekkes")
            if (fnrBruker.count() != 11) error("Fnr er ikke gyldig (må være 11 siffre)")

            // Sjekk om det allerede eksisterer et vedtak for barnet det siste året
            val harVedtak = vedtakStore.harFåttBrilleSisteÅret(fnrBruker)

            // Slå opp personinformasjon om barnet
            val personInformasjon = pdlService.hentPersonDetaljer(fnrBruker)
            val fultNavn = "${personInformasjon.fornavn} ${personInformasjon.etternavn}"
            val adresse = "${personInformasjon.adresse}, ${personInformasjon.postnummer} ${personInformasjon.poststed}"
            val forGammel = personInformasjon.alder!! > 18

            data class Response(
                val navn: String,
                val adresse: String,
                val kanSøke: Boolean,
            )

            call.respond(Response(fultNavn, adresse, !harVedtak && !forGammel))
        }

        authenticate(TOKEN_X_AUTH) {
        }
    }

    setupMetrics()

    if (Configuration.profile == Profile.LOCAL) {
        WiremockConfig().wiremockServer()
    }
}
