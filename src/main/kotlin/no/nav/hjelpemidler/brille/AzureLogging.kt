package no.nav.hjelpemidler.brille

import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.httpMethod
import io.ktor.server.request.uri
import mu.KotlinLogging

private val sikkerlogg = KotlinLogging.logger("tjenestekall")

fun ApplicationCall.adminAuditLogging(tag: String, params: Map<String, String?>) {
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
}
