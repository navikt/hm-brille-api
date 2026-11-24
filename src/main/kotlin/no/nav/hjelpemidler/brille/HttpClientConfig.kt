package no.nav.hjelpemidler.brille

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import no.nav.hjelpemidler.configuration.Environment
import no.nav.hjelpemidler.http.openid.Texas
import no.nav.hjelpemidler.http.openid.TokenSetProvider
import no.nav.hjelpemidler.http.openid.asApplication

fun engineFactory(block: () -> HttpClientEngine): HttpClientEngine =
    if (Environment.current.isLocal) {
        block()
    } else {
        CIO.create()
    }

fun Texas.entraIdApplication(scope: String): TokenSetProvider {
    val provider = entraId(scope)
    return TokenSetProvider { request ->
        request.asApplication()
        provider(request)
    }
}
