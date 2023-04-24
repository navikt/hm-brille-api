package no.nav.hjelpemidler.brille

import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.httpMethod
import io.ktor.server.request.uri
import mu.KotlinLogging
import java.util.UUID

private val sikkerlogg = KotlinLogging.logger("tjenestekall")

fun ApplicationCall.adminAuditLogging(tag: String, params: Map<String, String?>, fnrDetGjelder: String? = null) {
    val defaultParams: Map<String, String?> = mapOf(
        "uri" to request.uri,
        "method" to request.httpMethod.value,
        "oid" to extractUUID().toString(),
        "email" to extractEmail(),
        "name" to extractName()
    )

    val allParams = defaultParams.toMutableMap()
    allParams.putAll(params)

    val logMessage =
        "Admin api audit: $tag: ${jsonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(allParams)}"
    sikkerlogg.info(logMessage)

    adminAuditLog(request.httpMethod.value, request.uri, params, extractNavIdent(), fnrDetGjelder)
}

private val adminAuditLogger = KotlinLogging.logger("auditLogger")
private fun adminAuditLog(method: String, uri: String, params: Map<String, String?>, navIdent: String?, fnrDetGjelder: String?) {
    val message = listOf(
        "CEF:0",
        "hm-brille-admin",
        "auditLog",
        "1.0",
        "audit:access",
        "hm-brille-admin",
        "INFO",
        mutableMapOf(
            "end" to System.currentTimeMillis(),
            "sproc" to UUID.randomUUID(),
            "requestMethod" to method,
            "request" to uri.substring(
                0,
                uri.length.coerceAtMost(70)
            ),
            "suid" to navIdent,
            "duid" to fnrDetGjelder,
        ).apply {
            // Add all extra params
            this.putAll(params)
        }
            .filterValues { value ->  value == null }
            .map { (key, value) ->
                "$key=${value.toString()}"
            }.joinToString(" ")
    ).joinToString("|")

    if (Configuration.dev) {
        sikkerlogg.info("DEBUG: adminAuditLog log message: $message")
    }

    adminAuditLogger.info(message)
}
