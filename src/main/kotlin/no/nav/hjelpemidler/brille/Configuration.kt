package no.nav.hjelpemidler.brille

import com.natpryce.konfig.ConfigurationMap
import com.natpryce.konfig.ConfigurationProperties.Companion.systemProperties
import com.natpryce.konfig.EnvironmentVariables
import com.natpryce.konfig.Key
import com.natpryce.konfig.overriding
import com.natpryce.konfig.stringType
import no.nav.hjelpemidler.configuration.EnvironmentVariable
import java.time.Duration
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.Locale

object Configuration {
    private val defaultProperties = ConfigurationMap(
        mapOf(
            "unleash.unleash-uri" to "https://unleash.nais.io/api/",
            "kafka.client-id" to "hm-brille-api-v1",
            "kafka.topic" to "teamdigihot.hm-soknadsbehandling-v1",
            "digihot-oppslag.oppslagUrl" to "http://digihot-oppslag/api",
            "userclaim" to "pid",
            "enhetsregisteret_base_url" to "https://data.brreg.no",
            "altinn.altinnUrl" to "",
            "altinn.proxyConsumerId" to "",
            "ALTINN_APIKEY" to "",
            "ALTINN_APIGW_APIKEY" to "",
            "UTBETALING_ENABLED" to "false",
            "ELECTOR_PATH" to "",
            "hotsak.apiUrl" to "http://host.docker.internal:8089/hotsak",
            "hotsak.apiScope" to "api://dev-gcp.hotsak.hotsak-api/.default",
        ),
    )

    private val localProperties = ConfigurationMap(
        mapOf(
            "application.profile" to "LOCAL",
            "application.cluster" to "LOCAL",
            "DB_DATABASE" to "hm-brille-api-db-local",
            "DB_USERNAME" to "cloudsqliamuser",
            "DB_PASSWORD" to "dockerpass",
            "DB_HOST" to "localhost",
            "DB_PORT" to "5440",
            "pdfgen.rest-uri" to "http://host.docker.internal:8088",
            "pdl.graphql-uri" to "http://host.docker.internal:8089/pdl",
            "pdl.apiScope" to "api://dev-gcp.pdl.pdl-api/.default",
            "syfohelsenettproxy.rest-uri" to "http://host.docker.internal:8089/syfohelsenettproxy",
            "syfohelsenettproxy.scope" to "api://dev-fss.teamsykmelding.syfohelsenettproxy/.default",
            "medlemskap.oppslag.rest-uri" to "http://host.docker.internal:8089/medlemskapoppslag",
            "medlemskap.oppslag.scope" to "api://dev-gcp.medlemskap.medlemskap-barn/.default",
            "AZURE_OPENID_CONFIG_TOKEN_ENDPOINT" to "http://host.docker.internal:8080/default/token",
            "AZURE_APP_TENANT_ID" to "123",
            "AZURE_APP_CLIENT_ID" to "321",
            "AZURE_APP_CLIENT_SECRET" to "dummy",
            "AZURE_APP_WELL_KNOWN_URL" to "dummy",
            "KAFKA_BROKERS" to "localhost:9092",
            "TOKEN_X_WELL_KNOWN_URL" to "http://host.docker.internal:8080/default/.well-known/openid-configuration",
            "TOKEN_X_CLIENT_ID" to "local",
            "userclaim" to "sub",
            "REDIS_HOST" to "localhost",
            "REDIS_PASSWORD" to "",
            "SLACK_HOOK" to "http://dummy",
            "hotsak.apiUrl" to "http://host.docker.internal:8089/hotsak",
            "hotsak.apiScope" to "api://dev-gcp.hotsak.hotsak-api/.default",
        ),
    )

    private val devProperties = ConfigurationMap(
        mapOf(
            "kafka.client-id" to "hm-brille-api-v2",
            "application.profile" to "DEV",
            "application.cluster" to "DEV-GCP",
            "pdfgen.rest-uri" to "http://hm-soknad-pdfgen.teamdigihot.svc.cluster.local",
            "pdl.graphql-uri" to "https://pdl-api.dev-fss-pub.nais.io/graphql",
            "pdl.apiScope" to "api://dev-fss.pdl.pdl-api/.default",
            "syfohelsenettproxy.rest-uri" to "http://hm-mocks",
            "syfohelsenettproxy.scope" to "api://dev-gcp.teamsykmelding.syfohelsenettproxy/.default",
            "medlemskap.oppslag.rest-uri" to "http://medlemskap-barn.medlemskap.svc.cluster.local/",
            "medlemskap.oppslag.scope" to "api://dev-gcp.medlemskap.medlemskap-barn/.default",
            "enhetsregisteret_base_url" to "http://hm-mocks",
            "altinn.altinnUrl" to "https://api-gw-q1.oera.no/ekstern/altinn/api/serviceowner",
            "altinn.proxyConsumerId" to "hjelpemidlerdigitalsoknad-api-dev",
            "hotsak.apiUrl" to "http://hm-saksbehandling.teamdigihot.svc.cluster.local/api",
            "hotsak.apiScope" to "api://dev-gcp.teamdigihot.hm-saksbehandling/.default",
        ),
    )

    private val prodProperties = ConfigurationMap(
        mapOf(
            "application.profile" to "PROD",
            "application.cluster" to "PROD-GCP",
            "pdfgen.rest-uri" to "http://hm-soknad-pdfgen.teamdigihot.svc.cluster.local",
            "pdl.graphql-uri" to "https://pdl-api.prod-fss-pub.nais.io/graphql",
            "pdl.apiScope" to "api://prod-fss.pdl.pdl-api/.default",
            "syfohelsenettproxy.rest-uri" to "http://syfohelsenettproxy.teamsykmelding.svc.cluster.local",
            "syfohelsenettproxy.scope" to "api://prod-gcp.teamsykmelding.syfohelsenettproxy/.default",
            "medlemskap.oppslag.rest-uri" to "http://medlemskap-barn.medlemskap.svc.cluster.local/",
            "medlemskap.oppslag.scope" to "api://prod-gcp.medlemskap.medlemskap-barn/.default",
            "enhetsregisteret_base_url" to "https://data.brreg.no",
            "altinn.altinnUrl" to "https://api-gw.oera.no/ekstern/altinn/api/serviceowner",
            "altinn.proxyConsumerId" to "hjelpemidlerdigitalsoknad-api-prod",
            "hotsak.apiUrl" to "http://hm-saksbehandling.teamdigihot.svc.cluster.local/api",
            "hotsak.apiScope" to "api://prod-gcp.teamdigihot.hm-saksbehandling/.default",
        ),
    )

    private val resourceProperties =
        when (System.getenv("NAIS_CLUSTER_NAME") ?: System.getProperty("NAIS_CLUSTER_NAME")) {
            "dev-gcp" -> devProperties
            "prod-gcp" -> prodProperties
            else -> localProperties
        }

    private val config = systemProperties() overriding EnvironmentVariables() overriding resourceProperties overriding defaultProperties

    val profile: Profile = this["application.profile"].let { Profile.valueOf(it) }
    val cluster: Cluster = this["application.cluster"].let { Cluster.valueOf(it) }
    val local: Boolean = profile == Profile.LOCAL
    val dev: Boolean = profile == Profile.DEV
    val prod: Boolean = profile == Profile.PROD

    val gitCommit: String = getOrNull("GIT_COMMIT") ?: "unknown"

    val locale = Locale("nb")

    val dbProperties = DatabaseProperties()
    val kafkaProperties = KafkaProperties()
    val pdlProperties = PdlProperties()
    val tokenXProperties = TokenXProperties()
    val enhetsregisteretProperties = EnhetsregisteretProperties()
    val syfohelsenettproxyProperties = SyfohelsenettproxyProperties()
    val medlemskapOppslagProperties = MedlemskapOppslagProperties()
    val redisProperties = RedisProperties()
    val altinnProperties = AltinnProperties()
    val slackProperties = SlackProperties()
    val electorPath = get("ELECTOR_PATH")
    val hotsakApiProperties = HotsakApiProperties()

    operator fun get(key: String): String = config[Key(key, stringType)]
    fun getOrNull(key: String): String? = config.getOrNull(Key(key, stringType))

    data class DatabaseProperties(
        val databaseNavn: String = this["DB_DATABASE"],
        val databaseUser: String = this["DB_USERNAME"],
        val databasePassword: String = this["DB_PASSWORD"],
        val databaseHost: String = this["DB_HOST"],
        val databasePort: String = this["DB_PORT"],
    )

    data class KafkaProperties(
        val clientId: String = this["kafka.client-id"],
        val topic: String = this["kafka.topic"],
        val bootstrapServers: String = this["KAFKA_BROKERS"],
        val truststorePath: String? = getOrNull("KAFKA_TRUSTSTORE_PATH"),
        val truststorePassword: String? = getOrNull("KAFKA_CREDSTORE_PASSWORD"),
        val keystorePath: String? = getOrNull("KAFKA_KEYSTORE_PATH"),
        val keystorePassword: String? = getOrNull("KAFKA_CREDSTORE_PASSWORD"),
    )

    data class PdlProperties(
        val baseUrl: String = this["pdl.graphql-uri"],
        val scope: String = this["pdl.apiScope"],
    )

    data class HotsakApiProperties(
        val baseUrl: String = this["hotsak.apiUrl"],
        val scope: String = this["hotsak.apiScope"],
    )

    data class TokenXProperties(
        val userclaim: String = this["userclaim"],
    )

    data class EnhetsregisteretProperties(
        val baseUrl: String = this["enhetsregisteret_base_url"],
    )

    data class SyfohelsenettproxyProperties(
        val baseUrl: String = this["syfohelsenettproxy.rest-uri"],
        val scope: String = this["syfohelsenettproxy.scope"],
    )

    data class MedlemskapOppslagProperties(
        val baseUrl: String = this["medlemskap.oppslag.rest-uri"],
        val scope: String = this["medlemskap.oppslag.scope"],
    )

    data class RedisProperties(
        val host: String = this["REDIS_HOST"],
        val port: Int = 6379,
        val password: String = this["REDIS_PASSWORD"],
        val hprExpirySeconds: Long = Duration.ofDays(1).seconds,
        val medlemskapBarnExpiryDayOfMonth: Int = 7,
        val orgenhetExpirySeconds: Long = Duration.ofHours(2).seconds,
    ) {
        fun medlemskapBarnExpirySeconds(): Long = LocalDateTime.now().let { now ->
            val dt = if (now.dayOfMonth < medlemskapBarnExpiryDayOfMonth) {
                LocalDateTime.of(now.year, now.month, medlemskapBarnExpiryDayOfMonth, 0, 0)
            } else {
                val mo = (now.month.value % 12) + 1 // Advance by 1, wrap around to january
                val yr = if (now.month.value < mo) { now.year } else { now.year + 1 }
                LocalDateTime.of(yr, mo, medlemskapBarnExpiryDayOfMonth, 0, 0)
            }
            now.until(dt, ChronoUnit.SECONDS)
        }
    }

    data class AltinnProperties(
        val baseUrl: String = this["altinn.altinnUrl"],
        val proxyConsumerId: String = this["altinn.proxyConsumerId"],
        val apiKey: String = this["ALTINN_APIKEY"],
        val apiGWKey: String = this["ALTINN_APIGW_APIKEY"],
    )

    data class SlackProperties(
        val slackHook: String = this["SLACK_HOOK"],
        val environment: String = profile.toString(),
    )

    enum class Profile {
        LOCAL, DEV, PROD
    }

    enum class Cluster {
        `PROD-GCP`, `DEV-GCP`, `LOCAL`
    }

    val GRUPPE_TEAMDIGIHOT by EnvironmentVariable
    val GRUPPE_BRILLEADMIN_BRUKERE by EnvironmentVariable

    val CLIENT_ID_SAKSBEHANDLING by EnvironmentVariable
    val CLIENT_ID_BRILLE_INTEGRASJON by EnvironmentVariable
    val CLIENT_ID_AZURE_TOKEN_GENERATOR by EnvironmentVariable
}
