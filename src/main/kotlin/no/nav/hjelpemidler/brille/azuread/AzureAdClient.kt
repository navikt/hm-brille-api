package no.nav.hjelpemidler.brille.azuread

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import mu.KotlinLogging
import no.nav.hjelpemidler.brille.Configuration
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant

class AzureAdClient(
    private val props: Configuration.AzureAdProperties = Configuration.azureAdProperties,
) {
    private val tokenCache: MutableMap<String, Token> = mutableMapOf()

    fun getToken(scope: String) =
        tokenCache[scope]
            ?.takeUnless(Token::isExpired)
            ?: fetchToken(scope)
                .also { token ->
                    tokenCache[scope] = token
                }

    private fun fetchToken(scope: String): Token {
        val (responseCode, responseBody) = with(URL(props.openidConfigTokenEndpoint).openConnection() as HttpURLConnection) {
            requestMethod = "POST"
            connectTimeout = 10000
            readTimeout = 10000
            doOutput = true
            outputStream.use {
                it.bufferedWriter().apply {
                    write("client_id=${props.clientId}&client_secret=${props.clientSecret}&scope=$scope&grant_type=client_credentials")
                    flush()
                }
            }

            val stream: InputStream? = if (responseCode < 300) this.inputStream else this.errorStream
            responseCode to stream?.bufferedReader()?.readText()
        }

        if (responseBody == null) {
            throw RuntimeException("ukjent feil fra azure ad (responseCode=$responseCode), responseBody er null")
        }

        val jsonNode = objectMapper.readTree(responseBody)

        if (jsonNode.has("error")) {
            log.error("${jsonNode["error_description"].textValue()}: $jsonNode")
            throw RuntimeException("error from the azure token endpoint: ${jsonNode["error_description"].textValue()}")
        } else if (responseCode >= 300) {
            throw RuntimeException("unknown error (responseCode=$responseCode) from azure ad")
        }

        return Token(
            tokenType = jsonNode["token_type"].textValue(),
            expiresIn = jsonNode["expires_in"].longValue(),
            accessToken = jsonNode["access_token"].textValue()
        )
    }

    companion object {
        private val log = KotlinLogging.logger {}
        private val objectMapper = jacksonObjectMapper()
    }
}

private const val TOKEN_LEEWAY_SECONDS = 60

data class Token(val tokenType: String, val expiresIn: Long, val accessToken: String) {

    private val expiresOn = Instant.now().plusSeconds(expiresIn - TOKEN_LEEWAY_SECONDS)

    fun isExpired() = expiresOn.isBefore(Instant.now())
}
