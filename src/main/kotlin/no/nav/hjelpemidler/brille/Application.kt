package no.nav.hjelpemidler.brille

import com.fasterxml.jackson.databind.DeserializationFeature
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.authenticate
import io.ktor.server.plugins.callid.callIdMdc
import io.ktor.server.plugins.callloging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.path
import io.ktor.server.routing.IgnoreTrailingSlash
import io.ktor.server.routing.routing
import no.nav.hjelpemidler.brille.configurations.applicationConfig.HttpClientConfig.httpClient
import no.nav.hjelpemidler.brille.configurations.applicationConfig.MDC_CORRELATION_ID
import no.nav.hjelpemidler.brille.configurations.applicationConfig.setupCallId
import no.nav.hjelpemidler.brille.db.DatabaseConfig
import no.nav.hjelpemidler.brille.db.SoknadStorePostgres
import no.nav.hjelpemidler.brille.exceptions.configureStatusPages
import no.nav.hjelpemidler.brille.internal.selvtestRoutes
import no.nav.hjelpemidler.brille.internal.setupMetrics
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
    val httpClient = httpClient()

    val dataSource = DatabaseConfig(Configuration.dbProperties).dataSource()
    val soknadStore = SoknadStorePostgres(dataSource)

    installAuthentication(httpClient)

    routing {
        authenticate(TOKEN_X_AUTH) {
        }
        selvtestRoutes()
    }

    setupMetrics()

    if (Configuration.profile == Profile.LOCAL) {
        WiremockConfig().wiremockServer()
    }
}
