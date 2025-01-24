package no.nav.hjelpemidler.brille

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import no.nav.hjelpemidler.configuration.Environment

fun engineFactory(block: () -> HttpClientEngine): HttpClientEngine =
    if (Environment.current.isLocal) {
        block()
    } else {
        CIO.create()
    }
