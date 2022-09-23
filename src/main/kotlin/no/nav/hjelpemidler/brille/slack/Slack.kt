package no.nav.hjelpemidler.brille.slack

import com.fasterxml.jackson.databind.ObjectMapper
import no.nav.hjelpemidler.brille.Configuration
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

private val log = LoggerFactory.getLogger("PostToSlack")

object Slack {
    private val username = "hm-brille-api"
    private val environment = Configuration.slackProperties.environment
    private val hookUrl = Configuration.slackProperties.slackHook
    private val channel = "#digihot-barnebriller-alerts"

    fun post(message: String) {
        try {
            val slackMessage = "${environment.uppercase()} - $message"
            val values = mapOf(
                "text" to slackMessage,
                "channel" to channel,
                "username" to username,
            )

            val objectMapper = ObjectMapper()
            val requestBody: String = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(values)

            val client = HttpClient.newBuilder().build()
            val request = HttpRequest.newBuilder()
                .uri(URI.create(hookUrl))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build()
            client.send(request, HttpResponse.BodyHandlers.ofString())
        } catch (e: Exception) {
            log.warn("Posting av varsel til slack feilet.", e)
        }
    }
}
