package no.nav.hjelpemidler.brille.altinn

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.request
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.contentType
import io.ktor.serialization.jackson.jackson
import mu.KotlinLogging
import no.nav.hjelpemidler.brille.Configuration
import no.nav.hjelpemidler.brille.jsonMapper

private val log = KotlinLogging.logger { }

class AltinnClient(properties: Configuration.AltinnProperties) {
    private val client: HttpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            jackson {
                disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            }
        }
        defaultRequest {
            headers {
                accept(ContentType.Application.Json)
                contentType(ContentType.Application.Json)
                header("X-Consumer-ID", properties.proxyConsumerId)
                header("X-NAV-APIKEY", properties.apiGWKey)
                header("APIKEY", properties.apiKey)
            }
        }
    }
    private val baseUrl = properties.baseUrl

    suspend fun get(path: String, queryParameters: Parameters): JsonNode {
        val url = "$baseUrl/$path"
        log.info { "URL: $url, queryParameters: $queryParameters" }
        val response = client.get(url) {
            this.url.parameters.appendAll(queryParameters)
        }
        log.info { "Final URL: ${response.request.url}" }
        if (response.status == HttpStatusCode.OK) {
            return response.body()
        }
        kotlin.runCatching {
            val body = response.body<String>()
            log.warn { body }
        }
        return jsonMapper.nullNode()
    }

    suspend fun hentReportee(fnr: String, etternavn: String): Reportee? {
        val response = client.post("$baseUrl/reportees/ReporteeConversion") {
            log.info { "Henter reportee med fnr: $fnr, etternavn: $etternavn" }
            setBody(ReporteeConversion(fnr, etternavn))
        }
        if (response.status == HttpStatusCode.OK) {
            return response.body()
        }
        log.warn { "Fant ikke reportee, status: ${response.status}" }
        kotlin.runCatching {
            val body = response.body<String>()
            log.warn { body }
        }
        return null // todo -> feilhåndtering
    }

    suspend fun hentRightHolder(reporteeId: String): RightHolder? {
        val response = client.get("$baseUrl/authorization/Delegations/$reporteeId")
        if (response.status == HttpStatusCode.OK) {
            return response.body()
        }
        log.warn { "Fant ikke right holder, status: ${response.status}" }
        kotlin.runCatching {
            val body = response.body<String>()
            log.warn { body }
        }
        return null // todo -> feilhåndtering
    }
}

data class ReporteeConversion(
    @JsonProperty("SocialSecurityNumber")
    val socialSecurityNumber: String,
    @JsonProperty("LastName")
    val lastName: String,
)

data class Reportee(
    @JsonProperty("ReporteeId")
    val reporteeId: String,
)

/*
{
    "RightHolderId":"r50828869",
    "Name":"TOM HEIS",
    "LastName":"HEIS",
    "SocialSecurityNumber":"268992*****",
    "Roles":{
        "_links":{
            "self":{
                "href": ".../api/my/authorization/delegations/r50828869/roles"
            }
        },
        "_embedded":{
            "roles":[
                {
                    "RoleType":"Local",
                    "RoleDefinitionId":0,
                    "RoleName":"Single Rights",
                    "RoleDescription":"Collection of single rights",
                    "Delegator":"",
                    "DelegatedTime":"2021-12-01T11:57:43.373",
                    "_links":{
                        "roledefinition":{
                            "href": "...i/my/authorization/roledefinitions/0"
                        }
                    }
                },
                ...
            ]
        }
    },
}
 */

data class RightHolder(
    @JsonProperty("Roles")
    val roles: Map<String, Any?>,
)
