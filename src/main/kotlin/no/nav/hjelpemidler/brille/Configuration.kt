package no.nav.hjelpemidler.brille

import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.hjelpemidler.configuration.EnvironmentVariable
import no.nav.hjelpemidler.configuration.External
import no.nav.hjelpemidler.localization.LOCALE_NORWEGIAN_BOKMÅL
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

private val log = KotlinLogging.logger {}

object Configuration {
    val LOCALE = LOCALE_NORWEGIAN_BOKMÅL

    val ALTINN_URL by EnvironmentVariable

    val CLIENT_ID_AZURE_TOKEN_GENERATOR by EnvironmentVariable
    val CLIENT_ID_BRILLE_INTEGRASJON by EnvironmentVariable
    val CLIENT_ID_SAKSBEHANDLING by EnvironmentVariable

    /**
     * @see <a href="https://doc.nais.io/services/leader-election/index.html">Leader Election</a>
     */
    @External
    val ELECTOR_GET_URL by EnvironmentVariable

    val ENHETSREGISTERET_API_URL by EnvironmentVariable

    val GIT_COMMIT by EnvironmentVariable

    val GRUPPE_BRILLEADMIN_BRUKERE by EnvironmentVariable
    val GRUPPE_TEAMDIGIHOT by EnvironmentVariable

    val HOTSAK_API_SCOPE by EnvironmentVariable
    val HOTSAK_API_URL by EnvironmentVariable

    val KAFKA_CONSUMER_GROUP_ID by EnvironmentVariable
    val KAFKA_TOPIC by EnvironmentVariable

    val MEDLEMSKAP_API_SCOPE by EnvironmentVariable
    val MEDLEMSKAP_API_URL by EnvironmentVariable

    val PDL_API_SCOPE by EnvironmentVariable
    val PDL_API_URL by EnvironmentVariable

    val SYFOHELSENETTPROXY_API_SCOPE by EnvironmentVariable
    val SYFOHELSENETTPROXY_API_URL by EnvironmentVariable

    @External
    val REDIS_HOST_BRILLE by EnvironmentVariable

    @External
    val REDIS_PORT_BRILLE by EnvironmentVariable

    @External
    val REDIS_USERNAME_BRILLE by EnvironmentVariable

    @External
    val REDIS_PASSWORD_BRILLE by EnvironmentVariable

    @External
    val UNLEASH_SERVER_API_URL by EnvironmentVariable

    @External
    val UNLEASH_SERVER_API_TOKEN by EnvironmentVariable

    // @External
    // val UNLEASH_SERVER_API_ENV by EnvironmentVariable

    val redisProperties = RedisProperties()

    data class RedisProperties(
        val host: String = REDIS_HOST_BRILLE,
        val port: Int = REDIS_PORT_BRILLE.toInt(),
        val username: String = REDIS_USERNAME_BRILLE,
        val password: String = REDIS_PASSWORD_BRILLE,
        val hprExpirySeconds: Long = 1.days.inWholeSeconds,
        val medlemskapBarnExpiryDayOfMonth: Int = 7,
        val orgenhetExpirySeconds: Long = 2.hours.inWholeSeconds,
    ) {
        init {
            log.info { "Bruker redis tjener: $host:$port (username: $username)" }
        }

        fun medlemskapBarnExpirySeconds(): Long = LocalDateTime.now().let { now ->
            val dt = if (now.dayOfMonth < medlemskapBarnExpiryDayOfMonth) {
                LocalDateTime.of(now.year, now.month, medlemskapBarnExpiryDayOfMonth, 0, 0)
            } else {
                val mo = (now.month.value % 12) + 1 // Advance by 1, wrap around to january
                val yr = if (now.month.value < mo) {
                    now.year
                } else {
                    now.year + 1
                }
                LocalDateTime.of(yr, mo, medlemskapBarnExpiryDayOfMonth, 0, 0)
            }
            now.until(dt, ChronoUnit.SECONDS)
        }
    }
}
