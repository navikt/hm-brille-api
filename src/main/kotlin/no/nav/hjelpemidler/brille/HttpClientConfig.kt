package no.nav.hjelpemidler.brille

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import no.nav.hjelpemidler.http.createHttpClient

object HttpClientConfig {
    fun httpClient(engine: HttpClientEngine = CIO.create()): HttpClient = createHttpClient(engine) {
        expectSuccess = true
        install(HttpTimeout)
    }
}

fun engineFactory(block: () -> HttpClientEngine): HttpClientEngine = when (Configuration.profile) {
    Configuration.Profile.LOCAL -> block()
    else -> CIO.create()
}
