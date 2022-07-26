package no.nav.hjelpemidler.brille.azuread

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.DeserializationFeature
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.forms.submitForm
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.serialization.jackson.jackson
import mu.KotlinLogging
import no.nav.hjelpemidler.brille.Configuration
import no.nav.hjelpemidler.brille.StubEngine
import no.nav.hjelpemidler.brille.engineFactory

private val log = KotlinLogging.logger {}

interface OpenIDClient {
    suspend fun getToken(scope: String): BearerTokens
}

class AzureAdClient(
    private val props: Configuration.AzureAdProperties = Configuration.azureAdProperties,
    engine: HttpClientEngine = engineFactory { StubEngine.azureAd() },
) : OpenIDClient {
    private val client = HttpClient(engine) {
        expectSuccess = false
        install(ContentNegotiation) {
            jackson {
                disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            }
        }
    }

    private suspend fun grant(scope: String): Token {
        log.info { "Henter token fra Azure AD, scope: $scope" }
        val response = client
            .submitForm(
                url = props.openidConfigTokenEndpoint,
                formParameters = Parameters.build {
                    append("grant_type", "client_credentials")
                    append("client_id", props.clientId)
                    append("client_secret", props.clientSecret)
                    append("scope", scope)
                },
            )
        if (response.status == HttpStatusCode.OK) {
            return response.body()
        }
        val messageAndError = runCatching {
            val error = response.body<TokenError>().toString()
            "Uventet svar fra Azure AD, status: ${response.status}, error: $error" to null
        }.getOrElse {
            "Uventet svar fra Azure AD, status: ${response.status}" to it
        }
        throw AzureAdClientException(messageAndError.first, messageAndError.second)
    }

    override suspend fun getToken(scope: String): BearerTokens = BearerTokens(grant(scope).accessToken, "")
}

class AzureAdClientException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

data class Token(
    @JsonProperty("access_token")
    val accessToken: String,
)

data class TokenError(
    @JsonProperty("error")
    val error: String? = null,
    @JsonProperty("error_description")
    val errorDescription: String? = null,
)

fun Auth.azureAd(client: OpenIDClient, scope: String) {
    bearer {
        loadTokens {
            log.info { "loadTokens med scope: $scope" }
            client.getToken(scope)
        }
        refreshTokens {
            log.info { "refreshTokens med scope: $scope" }
            client.getToken(scope)
        }
        sendWithoutRequest { true }
    }
}
