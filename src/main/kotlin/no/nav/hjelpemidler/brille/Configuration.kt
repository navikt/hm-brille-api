package no.nav.hjelpemidler.brille

import com.natpryce.konfig.ConfigurationMap
import com.natpryce.konfig.ConfigurationProperties.Companion.systemProperties
import com.natpryce.konfig.EnvironmentVariables
import com.natpryce.konfig.Key
import com.natpryce.konfig.overriding
import com.natpryce.konfig.stringType

object Configuration {
    private val defaultProperties = ConfigurationMap(
        mapOf(
            "unleash.unleash-uri" to "https://unleash.nais.io/api/",
            "kafka.client-id" to "hm-brille-api-v1",
            "kafka.topic" to "teamdigihot.hm-soknadsbehandling-v1",
            "altinn.serviceCode" to "5614",
            "altinn.serviceEditionCode" to "1",
            "digihot-oppslag.oppslagUrl" to "http://digihot-oppslag/api",
            "userclaim" to "pid",
            "enhetsregisteret_base_url" to "https://data.brreg.no/enhetsregisteret/api"
        )
    )

    private val localProperties = ConfigurationMap(
        mapOf(
            "application.profile" to "LOCAL",
            "DB_DATABASE" to "digihotdev",
            "DB_USERNAME" to "postgres",
            "DB_PASSWORD" to "dockerpass",
            "DB_HOST" to "localhost",
            "DB_PORT" to "5433",
            "pdfgen.rest-uri" to "http://host.docker.internal:8088",
            "pdl.graphql-uri" to "http://host.docker.internal:8089/pdl",
            "pdl.apiScope" to "api://dev-gcp.pdl.pdl-api/.default",
            "syfohelsenettproxy.rest-uri" to "http://host.docker.internal:8089/syfohelsenettproxy",
            "syfohelsenettproxy.scope" to "api://dev-fss.teamsykmelding.syfohelsenettproxy/.default",
            "AZURE_OPENID_CONFIG_TOKEN_ENDPOINT" to "http://host.docker.internal:8080/default/token",
            "AZURE_APP_TENANT_ID" to "123",
            "AZURE_APP_CLIENT_ID" to "321",
            "AZURE_APP_CLIENT_SECRET" to "dummy",
            "KAFKA_BROKERS" to "host.docker.internal:9092",
            "TOKEN_X_WELL_KNOWN_URL" to "http://host.docker.internal:8080/default/.well-known/openid-configuration",
            "TOKEN_X_CLIENT_ID" to "local",
            "userclaim" to "sub",
        )
    )

    private val devProperties = ConfigurationMap(
        mapOf(
            "application.profile" to "DEV",
            "pdfgen.rest-uri" to "http://hm-soknad-pdfgen.teamdigihot.svc.cluster.local",
            "pdl.graphql-uri" to "https://pdl-api.dev-fss-pub.nais.io/graphql",
            "pdl.apiScope" to "api://dev-fss.pdl.pdl-api/.default",
            "syfohelsenettproxy.rest-uri" to "https://syfohelsenettproxy.dev-fss-pub.nais.io",
            "syfohelsenettproxy.scope" to "api://dev-fss.teamsykmelding.syfohelsenettproxy/.default",
        )
    )

    private val prodProperties = ConfigurationMap(
        mapOf(
            "application.profile" to "PROD",
            "pdfgen.rest-uri" to "http://hm-soknad-pdfgen.teamdigihot.svc.cluster.local",
            "pdl.graphql-uri" to "https://pdl-api.prod-fss-pub.nais.io/graphql",
            "pdl.apiScope" to "api://prod-fss.pdl.pdl-api/.default",
            "syfohelsenettproxy.rest-uri" to "https://syfohelsenettproxy.prod-fss-pub.nais.io",
            "syfohelsenettproxy.scope" to "api://prod-fss.teamsykmelding.syfohelsenettproxy/.default",
        )
    )

    private val resourceProperties =
        when (System.getenv("NAIS_CLUSTER_NAME") ?: System.getProperty("NAIS_CLUSTER_NAME")) {
            "dev-gcp" -> devProperties
            "prod-gcp" -> prodProperties
            else -> localProperties
        }

    val config =
        systemProperties() overriding EnvironmentVariables() overriding resourceProperties overriding defaultProperties

    val profile: Profile = this["application.profile"].let { Profile.valueOf(it) }

    val azureAdProperties = AzureAdProperties()
    val dbProperties = DatabaseProperties()
    val kafkaProperties = KafkaProperties()
    val pdfProperties = PdfProperties()
    val pdlProperties = PdlProperties()
    val tokenXProperties = TokenXProperties()
    val enhetsregisteretProperties = EnhetsregisteretProperties()
    val syfohelsenettproxyProperties = SyfohelsenettproxyProperties()

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
        val graphqlUri: String = this["pdl.graphql-uri"],
        val apiScope: String = this["pdl.apiScope"],
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
}

enum class Profile {
    LOCAL, DEV, PROD
}
