package no.nav.hjelpemidler.brille.altinn

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.DeserializationFeature
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.headers
import io.ktor.client.statement.request
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.jackson.jackson
import mu.KotlinLogging
import no.nav.hjelpemidler.brille.Configuration

private val log = KotlinLogging.logger { }
private val sikkerLog = KotlinLogging.logger("tjenestekall")

class AltinnClient(props: Configuration.AltinnProperties) {
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
                header("X-Consumer-ID", props.proxyConsumerId)
                header("X-NAV-APIKEY", props.apiGWKey)
                header("APIKEY", props.apiKey)
            }
        }
    }
    private val baseUrl = props.baseUrl

    suspend fun hentAvgivere(fnr: String): List<Avgiver> {
        val response = client.get("$baseUrl/reportees") {
            url {
                parameters.append("ForceEIAuthentication", "true")
                parameters.append("subject", fnr)
                parameters.append("\$filter", "Type ne 'Person' and Status eq 'Active'")
                parameters.append("\$top", "200")
            }
        }
        sikkerLog.info { "Hentet avgivere med url: ${response.request.url}" }
        if (response.status == HttpStatusCode.OK) {
            return response.body() ?: emptyList()
        }
        log.warn { "Kunne ikke hente avgivere, status: ${response.status}" }
        return emptyList()
    }

    suspend fun hentRettigheter(fnr: String, orgnr: String): Set<Avgiver.Rettighet> {
        val response = client.get("$baseUrl/authorization/rights") {
            url {
                parameters.append("ForceEIAuthentication", "true")
                parameters.append("subject", fnr)
                parameters.append("reportee", orgnr)
                parameters.append("\$filter", Avgiver.Rettighet.FILTER)
            }
        }
        sikkerLog.info { "Hentet rettigheter med url: ${response.request.url}" }
        if (response.status == HttpStatusCode.OK) {
            return response.body<HentRettigheterResponse?>()?.tilSet() ?: emptySet()
        }
        log.warn { "Kunne ikke hente rettigheter, status: ${response.status}" }
        return emptySet()
    }

    suspend fun hentRoller(fnr: String, orgnr: String): Set<Avgiver.Rolle> {
        val response = client.get("$baseUrl/authorization/roles") {
            url {
                parameters.append("ForceEIAuthentication", "true")
                parameters.append("subject", fnr)
                parameters.append("reportee", orgnr)
                parameters.append("\$filter", Avgiver.Rolle.FILTER)
            }
        }
        sikkerLog.info { "Hentet roller med url: ${response.request.url}" }
        if (response.status == HttpStatusCode.OK) {
            return response.body<HentRollerResponse?>()?.tilSet() ?: emptySet()
        }
        log.warn { "Kunne ikke hente roller, status: ${response.status}" }
        return emptySet()
    }

    data class Rettighet(
        @JsonProperty("ServiceCode") val kode: String,
        @JsonProperty("ServiceEditionCode") val versjon: Int,
    )

    data class Rolle(@JsonProperty("RoleDefinitionCode") val kode: String)

    private data class HentRettigheterResponse(@JsonProperty("Rights") val rettigheter: List<Rettighet>) {
        fun tilSet(): Set<Avgiver.Rettighet> = rettigheter.mapNotNull {
            Avgiver.Rettighet.fra(it.kode, it.versjon)
        }.toSet()
    }

    private data class HentRollerResponse(val roller: MutableList<Rolle> = mutableListOf()) :
        MutableList<Rolle> by roller {
        fun tilSet(): Set<Avgiver.Rolle> = roller.mapNotNull {
            Avgiver.Rolle.fra(it.kode)
        }.toSet()
    }
}
