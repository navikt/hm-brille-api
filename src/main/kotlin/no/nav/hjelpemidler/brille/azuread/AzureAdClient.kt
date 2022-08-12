package no.nav.hjelpemidler.brille.azuread

import com.fasterxml.jackson.annotation.JsonIgnore
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import mu.KotlinLogging
import no.nav.hjelpemidler.brille.Configuration
import no.nav.hjelpemidler.brille.StubEngine
import no.nav.hjelpemidler.brille.engineFactory
import java.time.Instant

private val log = KotlinLogging.logger {}

class AzureAdClient(
    private val props: Configuration.AzureAdProperties,
    engine: HttpClientEngine = engineFactory { StubEngine.azureAd() },
) {
    private val mutex = Mutex()
    private val tokenCache: MutableMap<String, Token> = mutableMapOf()

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

    suspend fun getToken(scope: String): BearerTokens = BearerTokens(grant(scope).accessToken, "")

    suspend fun getTokenCached(scope: String): Token = mutex.withLock {
        tokenCache[scope]
            ?.takeUnless(Token::isExpired)
            ?: grant(scope)
                .also { token ->
                    tokenCache[scope] = token
                }
    }
}

class AzureAdClientException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

data class Token(
    @JsonProperty("expires_in")
    val expiresIn: Long,
    @JsonProperty("access_token")
    val accessToken: String,
) {
    @JsonIgnore
    private val expiresOn: Instant = Instant.now().plusSeconds(expiresIn - TOKEN_LEEWAY_SECONDS)

    @JsonIgnore
    fun isExpired(): Boolean {
        return expiresOn.isBefore(Instant.now())
    }

    @JsonIgnore
    fun toBearerTokens(): BearerTokens = BearerTokens(accessToken, "")

    companion object {
        private const val TOKEN_LEEWAY_SECONDS = 60
    }
}

data class TokenError(
    @JsonProperty("error")
    val error: String? = null,
    @JsonProperty("error_description")
    val errorDescription: String? = null,
)

fun Auth.azureAd(scope: String) {
    val client = AzureAdClient(Configuration.azureAdProperties)
    bearer {
        loadTokens {
            client.getToken(scope)
        }
        refreshTokens {
            client.getToken(scope)
        }
        sendWithoutRequest { true }
    }
}
