package no.nav.hjelpemidler.brille.pdl

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
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
import mu.KotlinLogging
import no.nav.hjelpemidler.brille.StubEngine
import no.nav.hjelpemidler.brille.azuread.AzureAdClient
import no.nav.hjelpemidler.brille.engineFactory
import java.util.UUID

private val log = KotlinLogging.logger {}

class PdlClient(
    private val baseUrl: String,
    private val scope: String,
    private val azureAdClient: AzureAdClient,
    engine: HttpClientEngine = engineFactory { StubEngine.pdl() },
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

    suspend fun hentPerson(fnummer: String): PdlPersonResponse {
        val hentPersonDetaljerQuery = hentPersonQuery(fnummer)
        return pdlRequest(hentPersonDetaljerQuery)
    }

    suspend fun medlemskapHentBarn(fnummer: String): PdlPersonResponse {
        val hentPersonDetaljerQuery = medlemskapHentBarnQuery(fnummer)
        return pdlRequest(hentPersonDetaljerQuery)
    }

    suspend fun medlemskapHentVergeEllerForelder(fnummer: String): PdlPersonResponse {
        val hentPersonDetaljerQuery = medlemskapHentVergeEllerForelderQuery(fnummer)
        return pdlRequest(hentPersonDetaljerQuery)
    }

    private suspend inline fun <reified T : Any> pdlRequest(pdlQuery: PersonGraphqlQuery): T {
        return withContext(Dispatchers.IO) {
            val response = client
                .post(baseUrl) {
                    headers {
                        contentType(Json)
                        accept(Json)
                        header("Tema", "HJE")
                        header("X-Correlation-ID", UUID.randomUUID().toString())
                    }
                    setBody(pdlQuery)
                }

            response.body()
        }
    }
}
