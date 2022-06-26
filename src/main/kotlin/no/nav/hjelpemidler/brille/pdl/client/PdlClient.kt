package no.nav.hjelpemidler.brille.pdl.client

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.ktor.client.HttpClient
import io.ktor.client.call.body
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
import no.nav.hjelpemidler.brille.Configuration
import no.nav.hjelpemidler.brille.azuread.AzureAdClient
import no.nav.hjelpemidler.brille.pdl.model.PdlIdentResponse
import no.nav.hjelpemidler.brille.pdl.model.PdlPersonResponse
import no.nav.hjelpemidler.brille.pdl.model.PersonGraphqlQuery
import no.nav.hjelpemidler.brille.pdl.model.hentIdenterQuery
import no.nav.hjelpemidler.brille.pdl.model.hentPersonDetaljerQuery
import no.nav.hjelpemidler.brille.pdl.model.hentPersonQuery
import java.util.UUID

class PdlClient(
    private val azureAdClient: AzureAdClient,
    private val pdlProperties: Configuration.PdlProperties = Configuration.pdlProperties,
) {
    private val client = HttpClient(CIO) {
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
                loadTokens { azureAdClient.getToken(pdlProperties.apiScope).toBearerTokens() }
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
        val pdlUrl = pdlProperties.graphqlUri

        return withContext(Dispatchers.IO) {
            client.post(pdlUrl) {
                headers {
                    contentType(Json)
                    accept(Json)
                    header("Tema", "HJE")
                    header("X-Correlation-ID", UUID.randomUUID().toString())
                }
                setBody(pdlQuery)
            }.body()
        }
    }
}
