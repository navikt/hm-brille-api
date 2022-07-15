package no.nav.hjelpemidler.brille.azuread

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.DeserializationFeature
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.*
import io.ktor.client.plugins.auth.providers.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import mu.KotlinLogging
import no.nav.hjelpemidler.brille.Configuration
import no.nav.hjelpemidler.brille.StubEngine
import no.nav.hjelpemidler.brille.engineFactory
import java.time.Instant
import kotlin.collections.MutableMap
import kotlin.collections.joinToString
import kotlin.collections.mutableMapOf
import kotlin.collections.set

private val log = KotlinLogging.logger {}

class AzureAdClient(
    private val props: Configuration.AzureAdProperties = Configuration.azureAdProperties,
    engine: HttpClientEngine = engineFactory { StubEngine.azureAd() },
) {
    private val client = HttpClient(engine) {
        expectSuccess = false
        install(ContentNegotiation) {
            jackson {
                disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            }
        }
    }
    private val mutex = Mutex()
    private val tokenCache: MutableMap<String, Token> = mutableMapOf()

    private suspend fun grant(scope: String): Token {
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

    suspend fun getToken(scope: String): Token = mutex.withLock {

        log.info( "tokenCache: ${tokenCache.entries.joinToString { "${it.key}: ${it.value}" }}")
        log.info(" token ${tokenCache[scope]}")
        log.info(" token is expired? ${tokenCache[scope]?.isExpired() ?: "null"}")

        tokenCache[scope]
            ?.takeUnless(Token::isExpired)
            ?: grant(scope)
                .also { token ->
                    log.debug { "Token oppdatert, scope: $scope" }
                    tokenCache[scope] = token
                }
    }
}

class AzureAdClientException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

data class Token(
    @JsonProperty("token_type")
    val tokenType: String,
    @JsonProperty("expires_in")
    val expiresIn: Long,
    @JsonProperty("access_token")
    val accessToken: String,
) {
    @JsonIgnore
    private val expiresOn: Instant = Instant.now().plusSeconds(expiresIn - TOKEN_LEEWAY_SECONDS)

    @JsonIgnore
    fun isExpired(): Boolean {
        log.info { "expires on: $expiresOn, now: ${Instant.now()}, expiresOnIsBefore: ${expiresOn.isBefore(Instant.now())}" }
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
