package no.nav.hjelpemidler.brille

import com.natpryce.konfig.ConfigurationMap
import com.natpryce.konfig.ConfigurationProperties.Companion.systemProperties
import com.natpryce.konfig.EnvironmentVariables
import com.natpryce.konfig.Key
import com.natpryce.konfig.overriding
import com.natpryce.konfig.stringType
import java.time.Duration
import java.util.Locale

object Configuration {
    private val defaultProperties = ConfigurationMap(
        mapOf(
            "unleash.unleash-uri" to "https://unleash.nais.io/api/",
            "kafka.client-id" to "hm-brille-api-v1",
            "kafka.topic" to "teamdigihot.hm-soknadsbehandling-v1",
            "digihot-oppslag.oppslagUrl" to "http://digihot-oppslag/api",
            "userclaim" to "pid",
            "enhetsregisteret_base_url" to "https://data.brreg.no/enhetsregisteret/api",
            "altinn.altinnUrl" to "",
            "altinn.proxyConsumerId" to "",
            "ALTINN_APIKEY" to "",
            "ALTINN_APIGW_APIKEY" to "",
            "UTBETALING_ENABLED" to "false",
            "ELECTOR_PATH" to ""
        )
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
            "medlemskap.oppslag.scope" to "api://dev-gcp.medlemskap.medlemskap-oppslag/.default",
            "AZURE_OPENID_CONFIG_TOKEN_ENDPOINT" to "http://host.docker.internal:8080/default/token",
            "AZURE_APP_TENANT_ID" to "123",
            "AZURE_APP_CLIENT_ID" to "321",
            "AZURE_APP_CLIENT_SECRET" to "dummy",
            "KAFKA_BROKERS" to "host.docker.internal:9092",
            "TOKEN_X_WELL_KNOWN_URL" to "http://host.docker.internal:8080/default/.well-known/openid-configuration",
            "TOKEN_X_CLIENT_ID" to "local",
            "userclaim" to "sub",
            "REDIS_HOST" to "localhost",
            "REDIS_PASSWORD" to "",
        )
    )

    private val devProperties = ConfigurationMap(
        mapOf(
            "application.profile" to "DEV",
            "application.cluster" to "DEV-GCP",
            "pdfgen.rest-uri" to "http://hm-soknad-pdfgen.teamdigihot.svc.cluster.local",
            "pdl.graphql-uri" to "https://pdl-api.dev-fss-pub.nais.io/graphql",
            "pdl.apiScope" to "api://dev-fss.pdl.pdl-api/.default",
            "syfohelsenettproxy.rest-uri" to "https://syfohelsenettproxy.dev-fss-pub.nais.io",
            "syfohelsenettproxy.scope" to "api://dev-fss.teamsykmelding.syfohelsenettproxy/.default",
            "medlemskap.oppslag.rest-uri" to "https://medlemskap-oppslag.dev.nav.no/",
            "medlemskap.oppslag.scope" to "api://dev-gcp.medlemskap.medlemskap-oppslag/.default",
            "enhetsregisteret_base_url" to "http://hm-mocks/brille/enhetsregisteret/api",
            "altinn.altinnUrl" to "https://api-gw-q1.oera.no/ekstern/altinn/api/serviceowner",
            "altinn.proxyConsumerId" to "hjelpemidlerdigitalsoknad-api-dev",
        )
    )

    private val prodProperties = ConfigurationMap(
        mapOf(
            "application.profile" to "PROD",
            "application.cluster" to "PROD-GCP",
            "pdfgen.rest-uri" to "http://hm-soknad-pdfgen.teamdigihot.svc.cluster.local",
            "pdl.graphql-uri" to "https://pdl-api.prod-fss-pub.nais.io/graphql",
            "pdl.apiScope" to "api://prod-fss.pdl.pdl-api/.default",
            "syfohelsenettproxy.rest-uri" to "https://syfohelsenettproxy.prod-fss-pub.nais.io",
            "syfohelsenettproxy.scope" to "api://prod-fss.teamsykmelding.syfohelsenettproxy/.default",
            "medlemskap.oppslag.rest-uri" to "https://medlemskap-oppslag.intern.nav.no/",
            "medlemskap.oppslag.scope" to "api://prod-gcp.medlemskap.medlemskap-oppslag/.default",
            "enhetsregisteret_base_url" to "https://data.brreg.no/enhetsregisteret/api",
            "altinn.altinnUrl" to "https://api-gw.oera.no/ekstern/altinn/api/serviceowner",
            "altinn.proxyConsumerId" to "hjelpemidlerdigitalsoknad-api-prod",
        )
    )

    private val cronjobProperties = ConfigurationMap(
        mapOf(
            "TOKEN_X_CLIENT_ID" to "abc",
            "TOKEN_X_WELL_KNOWN_URL" to "abc",

            "DB_DATABASE" to System.getenv("DB_NAISJOB_DATABASE"),
            "DB_USERNAME" to System.getenv("DB_NAISJOB_USERNAME"),
            "DB_PASSWORD" to System.getenv("DB_NAISJOB_PASSWORD"),
            "DB_HOST" to System.getenv("DB_NAISJOB_HOST"),
            "DB_PORT" to System.getenv("DB_NAISJOB_PORT"),
        )
    )

    private val resourceProperties =
        when (System.getenv("NAIS_CLUSTER_NAME") ?: System.getProperty("NAIS_CLUSTER_NAME")) {
            "dev-gcp" -> devProperties
            "prod-gcp" -> prodProperties
            else -> localProperties
        }

    private val config = when (System.getenv("CRONJOB_TYPE")) {
        null -> systemProperties() overriding EnvironmentVariables() overriding resourceProperties overriding defaultProperties
        else -> systemProperties() overriding EnvironmentVariables() overriding cronjobProperties overriding resourceProperties overriding defaultProperties
    }

    val profile: Profile = this["application.profile"].let { Profile.valueOf(it) }
    val cluster: Cluster = this["application.cluster"].let { Cluster.valueOf(it) }
    val local: Boolean = profile == Profile.LOCAL
    val dev: Boolean = profile == Profile.DEV
    val prod: Boolean = profile == Profile.PROD
    val cronjob: Boolean = System.getenv("CRONJOB_TYPE") != null

    val gitCommit: String = getOrNull("GIT_COMMIT") ?: "unknown"

    val locale = Locale("nb")

    val azureAdProperties = AzureAdProperties()
    val dbProperties = DatabaseProperties()
    val kafkaProperties = KafkaProperties()
    val pdfProperties = PdfProperties()
    val pdlProperties = PdlProperties()
    val tokenXProperties = TokenXProperties()
    val enhetsregisteretProperties = EnhetsregisteretProperties()
    val syfohelsenettproxyProperties = SyfohelsenettproxyProperties()
    val medlemskapOppslagProperties = MedlemskapOppslagProperties()
    val redisProperties = RedisProperties()
    val altinnProperties = AltinnProperties()
    val utbetalingProperties = UtbetalingProperties()
    val electorPath = get("ELECTOR_PATH")

    operator fun get(key: String): String = config[Key(key, stringType)]
    fun getOrNull(key: String): String? = config.getOrNull(Key(key, stringType))

    data class AllowlistProperties(
        val restUri: String = this["allowlist-api.rest-uri"],
    )

    data class AzureAdProperties(
        val openidConfigTokenEndpoint: String = this["AZURE_OPENID_CONFIG_TOKEN_ENDPOINT"],
        val tenantId: String = this["AZURE_APP_TENANT_ID"],
        val clientId: String = this["AZURE_APP_CLIENT_ID"],
        val clientSecret: String = this["AZURE_APP_CLIENT_SECRET"],
    )

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

    data class PdfProperties(
        val pdfgenUri: String = this["pdfgen.rest-uri"],
    )

    data class PdlProperties(
        val baseUrl: String = this["pdl.graphql-uri"],
        val scope: String = this["pdl.apiScope"],
    )

    data class TokenXProperties(
        val clientId: String = this["TOKEN_X_CLIENT_ID"],
        val wellKnownUrl: String = this["TOKEN_X_WELL_KNOWN_URL"],
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
        val medlemskapBarnExpirySeconds: Long = Duration.ofDays(1).seconds,
        val orgenhetExpirySeconds: Long = Duration.ofHours(2).seconds,
    )

    data class AltinnProperties(
        val baseUrl: String = this["altinn.altinnUrl"],
        val proxyConsumerId: String = this["altinn.proxyConsumerId"],
        val apiKey: String = this["ALTINN_APIKEY"],
        val apiGWKey: String = this["ALTINN_APIGW_APIKEY"],
    )

    data class UtbetalingProperties(
        val enabledUtbetaling: Boolean = "true" == this["UTBETALING_ENABLED"]
    )

    enum class Profile {
        LOCAL, DEV, PROD
    }

    enum class Cluster {
        `PROD-GCP`, `DEV-GCP`, `LOCAL`
    }
}
