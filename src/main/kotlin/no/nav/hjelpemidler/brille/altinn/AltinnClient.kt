package no.nav.hjelpemidler.brille.altinn

import com.fasterxml.jackson.annotation.JsonProperty
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.headers
import io.ktor.client.statement.request
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import no.nav.hjelpemidler.brille.Configuration
import no.nav.hjelpemidler.http.createHttpClient
import no.nav.hjelpemidler.logging.secureInfo

private val log = KotlinLogging.logger { }

const val ALTINN_CLIENT_MAKS_ANTALL_RESULTATER = 1000

class AltinnClient {
    private val client: HttpClient = createHttpClient {
        defaultRequest {
            headers {
                accept(ContentType.Application.Json)
                contentType(ContentType.Application.Json)
                header("X-Consumer-ID", Configuration.ALTINN_APIGW_CONSUMER_ID)
                header("X-NAV-APIKEY", Configuration.ALTINN_APIGW_APIKEY)
                header("APIKEY", Configuration.ALTINN_APIKEY)
            }
        }
    }
    private val baseUrl = Configuration.ALTINN_APIGW_URL

    suspend fun hentAvgivere(fnr: String, tjeneste: Avgiver.Tjeneste): List<Avgiver> {
        val response = client.get("$baseUrl/reportees") {
            url {
                parameters.append("ForceEIAuthentication", "true")
                parameters.append("subject", fnr)
                parameters.append("serviceCode", tjeneste.kode)
                parameters.append("serviceEdition", tjeneste.versjon.toString())
                parameters.append("\$filter", "Type ne 'Person' and Status eq 'Active'")
                parameters.append("\$top", ALTINN_CLIENT_MAKS_ANTALL_RESULTATER.toString()) // Default er mindre enn 200
            }
        }
        log.secureInfo { "Hentet avgivere med url: ${response.request.url} (status: ${response.status})" }
        if (response.status == HttpStatusCode.OK) {
            return response.body() ?: emptyList()
        }
        log.warn { "Kunne ikke hente avgivere, status: ${response.status}" }
        return emptyList()
    }

    suspend fun hentRettigheter(fnr: String, orgnr: String): Set<Avgiver.Tjeneste> {
        val response = client.get("$baseUrl/authorization/rights") {
            url {
                parameters.append("ForceEIAuthentication", "true")
                parameters.append("subject", fnr)
                parameters.append("reportee", orgnr)
                parameters.append("\$filter", Avgiver.Tjeneste.FILTER)
            }
        }
        log.secureInfo { "Hentet rettigheter med url: ${response.request.url}" }
        if (response.status == HttpStatusCode.OK) {
            return response.body<HentRettigheterResponse?>()?.tilSet() ?: emptySet()
        }
        log.warn { "Kunne ikke hente rettigheter, status: ${response.status}" }
        return emptySet()
    }

    private data class Rettighet(
        @JsonProperty("ServiceCode") val kode: String,
        @JsonProperty("ServiceEditionCode") val versjon: Int,
    )

    private data class HentRettigheterResponse(@JsonProperty("Rights") val rettigheter: List<Rettighet>) {
        fun tilSet(): Set<Avgiver.Tjeneste> = rettigheter.mapNotNull {
            Avgiver.Tjeneste.fra(it.kode, it.versjon)
        }.toSet()
    }
}
