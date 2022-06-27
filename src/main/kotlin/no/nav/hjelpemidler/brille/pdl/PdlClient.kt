package no.nav.hjelpemidler.brille.pdl

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.accept
import io.ktor.client.request.header
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType.Application.Json
import io.ktor.http.contentType
import io.ktor.serialization.jackson.jackson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.nav.hjelpemidler.brille.azuread.AzureAdClient
import java.util.UUID

class PdlClient(
    private val baseUrl: String,
    private val scope: String,
    private val azureAdClient: AzureAdClient,
    engine: HttpClientEngine = CIO.create(),
) {
    private val client = HttpClient(engine) {
        expectSuccess = true
        install(ContentNegotiation) {
            jackson {
                registerModule(JavaTimeModule())
                disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            }
        }
        install(Auth) {
            bearer {
                loadTokens { azureAdClient.getToken(scope).toBearerTokens() }
                refreshTokens { null }
                sendWithoutRequest { true }
            }
        }
    }

    suspend fun hentIdentInfo(fnummer: String): PdlIdentResponse {
        val hentIdenterQuery = hentIdenterQuery(fnummer)
        return pdlRequest(hentIdenterQuery)
    }

    suspend fun hentPersonInfo(fnummer: String): PdlPersonResponse {
        val hentPersonQuery = hentPersonQuery(fnummer)
        return pdlRequest(hentPersonQuery)
    }

    suspend fun hentPersonDetaljer(fnummer: String): PdlPersonResponse {
        val hentPersonDetaljerQuery = hentPersonDetaljerQuery(fnummer)
        return pdlRequest(hentPersonDetaljerQuery)
    }

    private suspend inline fun <reified T : Any> pdlRequest(pdlQuery: PersonGraphqlQuery): T {
        return withContext(Dispatchers.IO) {
            client
                .post(baseUrl) {
                    headers {
                        contentType(Json)
                        accept(Json)
                        header("Tema", "HJE")
                        header("X-Correlation-ID", UUID.randomUUID().toString())
                    }
                    setBody(pdlQuery)
                }
                .body()
        }
    }
}
