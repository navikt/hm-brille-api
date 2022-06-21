package no.nav.hjelpemidler.brille.configurations.applicationConfig

import io.ktor.http.HttpHeaders
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.callid.CallId
import java.util.UUID

const val MDC_CORRELATION_ID = "correlation-id"

private val uuidLength = UUID.randomUUID().toString().length

fun Application.setupCallId() {

    install(CallId) {
        // Spesifiser hvilken header callId skal hentes fra (og evt settes på dersom det mangler)
        header(HttpHeaders.XCorrelationId)

        // Generer ny callId dersom den mangler
        generate { UUID.randomUUID().toString() }

        // Verifiser at vi har en gyldig callId. (Generer ny dersom ugyldig)
        verify { callId: String ->
            callId.isNotBlank() && callId.length == uuidLength
        }
    }
}
