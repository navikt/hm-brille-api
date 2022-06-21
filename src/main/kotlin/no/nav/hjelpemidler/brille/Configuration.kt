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
        )
    )

    private val prodProperties = ConfigurationMap(
        mapOf(
            "application.profile" to "PROD",
            "pdfgen.rest-uri" to "http://hm-soknad-pdfgen.teamdigihot.svc.cluster.local",
            "pdl.graphql-uri" to "https://pdl-api.prod-fss-pub.nais.io/graphql",
            "pdl.apiScope" to "api://prod-fss.pdl.pdl-api/.default",
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

    val profile: Profile = config[Key("application.profile", stringType)].let { Profile.valueOf(it) }

    val azureAdProperties = AzureAdProperties()
    val dbProperties = DatabaseProperties()
    val kafkaProperties = KafkaProperties()
    val pdfProperties = PdfProperties()
    val pdlProperties = PdlProperties()
    val tokenXProperties = TokenXProperties()

    data class AllowlistProperties(
        val restUri: String = config[Key("allowlist-api.rest-uri", stringType)]
    )

    data class AzureAdProperties(
        val openidConfigTokenEndpoint: String = config[Key("AZURE_OPENID_CONFIG_TOKEN_ENDPOINT", stringType)],
        val tenantId: String = config[Key("AZURE_APP_TENANT_ID", stringType)],
        val clientId: String = config[Key("AZURE_APP_CLIENT_ID", stringType)],
        val clientSecret: String = config[Key("AZURE_APP_CLIENT_SECRET", stringType)]
    )

    data class DatabaseProperties(
        val databaseNavn: String = config[Key("DB_DATABASE", stringType)],
        val databaseUser: String = config[Key("DB_USERNAME", stringType)],
        val databasePassword: String = config[Key("DB_PASSWORD", stringType)],
        val databaseHost: String = config[Key("DB_HOST", stringType)],
        val databasePort: String = config[Key("DB_PORT", stringType)]
    )

    data class KafkaProperties(
        val clientId: String = config[Key("kafka.client-id", stringType)],
        val topic: String = config[Key("kafka.topic", stringType)],
        val bootstrapServers: String = config[Key("KAFKA_BROKERS", stringType)],
        val truststorePath: String? = config.getOrNull(Key("KAFKA_TRUSTSTORE_PATH", stringType)),
        val truststorePassword: String? = config.getOrNull(Key("KAFKA_CREDSTORE_PASSWORD", stringType)),
        val keystorePath: String? = config.getOrNull(Key("KAFKA_KEYSTORE_PATH", stringType)),
        val keystorePassword: String? = config.getOrNull(Key("KAFKA_CREDSTORE_PASSWORD", stringType))
    )

    data class PdfProperties(
        val pdfgenUri: String = config[Key("pdfgen.rest-uri", stringType)]
    )

    data class PdlProperties(
        val graphqlUri: String = config[Key("pdl.graphql-uri", stringType)],
        val apiScope: String = config[Key("pdl.apiScope", stringType)]
    )

    data class TokenXProperties(
        val clientId: String = config[Key("TOKEN_X_CLIENT_ID", stringType)],
        val wellKnownUrl: String = config[Key("TOKEN_X_WELL_KNOWN_URL", stringType)],
        val userclaim: String = config[Key("userclaim", stringType)],
    )
}

enum class Profile {
    LOCAL, DEV, PROD
}
