package no.nav.hjelpemidler.brille

import no.nav.hjelpemidler.configuration.EnvironmentVariable
import no.nav.hjelpemidler.configuration.External
import no.nav.hjelpemidler.localization.LOCALE_NORWEGIAN_BOKMÅL
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

object Configuration {
    val LOCALE = LOCALE_NORWEGIAN_BOKMÅL

    @External(secret = "hm-soknad-api-secret")
    val ALTINN_APIGW_APIKEY by EnvironmentVariable

    @External(secret = "hm-soknad-api-secret")
    val ALTINN_APIKEY by EnvironmentVariable

    val ALTINN_APIGW_CONSUMER_ID by EnvironmentVariable
    val ALTINN_APIGW_URL by EnvironmentVariable

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

    @External(secret = "redis-password")
    val REDIS_PASSWORD by EnvironmentVariable
    val REDIS_HOST by EnvironmentVariable

    val SYFOHELSENETTPROXY_API_SCOPE by EnvironmentVariable
    val SYFOHELSENETTPROXY_API_URL by EnvironmentVariable

    val redisProperties = RedisProperties()

    data class RedisProperties(
        val host: String = REDIS_HOST,
        val port: Int = 6379,
        val password: String = REDIS_PASSWORD,
        val hprExpirySeconds: Long = 1.days.inWholeSeconds,
        val medlemskapBarnExpiryDayOfMonth: Int = 7,
        val orgenhetExpirySeconds: Long = 2.hours.inWholeSeconds,
    ) {
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
