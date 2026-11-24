package no.nav.hjelpemidler.brille.slack

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.engine.cio.CIO
import no.nav.hjelpemidler.configuration.Environment
import no.nav.hjelpemidler.http.slack.slack
import no.nav.hjelpemidler.http.slack.slackIconEmoji

private val log = KotlinLogging.logger {}

object Slack {
    private const val USERNAME = "hm-brille-api"
    private const val CHANNEL_DEV = "#digihot-alerts-dev"
    private const val CHANNEL_PROD = "#digihot-barnebriller-alerts"

    private val tier = Environment.current.tier

    private val client = slack(CIO.create())

    suspend fun post(message: String) {
        try {
            client.sendMessage(
                username = USERNAME,
                icon = slackIconEmoji(":fire:"),
                channel = if (tier.isProd) CHANNEL_PROD else CHANNEL_DEV,
                message = "$tier - $message",
            )
        } catch (e: Exception) {
            log.warn(e) { "Posting av varsel til slack feilet." }
        }
    }
}
