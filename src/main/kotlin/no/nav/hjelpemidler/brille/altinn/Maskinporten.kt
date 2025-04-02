package no.nav.hjelpemidler.brille.altinn

import com.nimbusds.jose.JOSEObjectType
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import no.nav.hjelpemidler.configuration.MaskinportenEnvironmentVariable
import no.nav.hjelpemidler.http.DefaultHttpClientFactory
import no.nav.hjelpemidler.http.HttpClientFactory
import no.nav.hjelpemidler.http.openid.DefaultOpenIDConfiguration
import no.nav.hjelpemidler.http.openid.OpenIDClient
import no.nav.hjelpemidler.http.openid.OpenIDClientConfiguration
import no.nav.hjelpemidler.http.openid.OpenIDConfiguration
import no.nav.hjelpemidler.http.openid.TokenSetProvider
import no.nav.hjelpemidler.http.openid.createOpenIDClient
import no.nav.hjelpemidler.http.openid.openID
import no.nav.hjelpemidler.time.toDate
import java.time.LocalDateTime
import java.util.Date
import java.util.UUID

private val log = KotlinLogging.logger {}

fun maskinportenEnvironmentConfiguration(): OpenIDConfiguration = DefaultOpenIDConfiguration(
    tokenEndpoint = MaskinportenEnvironmentVariable.MASKINPORTEN_TOKEN_ENDPOINT,
    // Merk: Disse er ikke relevante for maskinporten backend'en, men kreves av hotlibs-http sin openid client
    clientId = MaskinportenEnvironmentVariable.MASKINPORTEN_CLIENT_ID,
    clientSecret = "<n/a>",
)

fun OpenIDClientConfiguration.maskinportenEnvironmentConfiguration() {
    tokenEndpoint = MaskinportenEnvironmentVariable.MASKINPORTEN_TOKEN_ENDPOINT
    // Merk: Disse er ikke relevante for maskinporten backend'en, men kreves av hotlibs-http sin openid client
    clientId = MaskinportenEnvironmentVariable.MASKINPORTEN_CLIENT_ID
    clientSecret = ""
}

fun maskinportenClient(
    httpClientFactory: HttpClientFactory = DefaultHttpClientFactory,
    block: OpenIDClientConfiguration.() -> Unit = {},
): OpenIDClient =
    createOpenIDClient(httpClientFactory = httpClientFactory) {
        maskinportenEnvironmentConfiguration()
        block()
    }

fun maskinportenClient(
    engine: HttpClientEngine,
    block: OpenIDClientConfiguration.() -> Unit = {},
): OpenIDClient =
    createOpenIDClient(engine = engine) {
        maskinportenEnvironmentConfiguration()
        block()
    }

fun OpenIDClient.withMaskinportenAssertion(scope: String): TokenSetProvider {
    return TokenSetProvider {
        val rsaKey = RSAKey.parse(MaskinportenEnvironmentVariable.MASKINPORTEN_CLIENT_JWK)
        val signedJWT = SignedJWT(
            JWSHeader.Builder(JWSAlgorithm.RS256)
                .keyID(rsaKey.keyID)
                .type(JOSEObjectType.JWT)
                .build(),
            JWTClaimsSet.Builder()
                .audience(MaskinportenEnvironmentVariable.MASKINPORTEN_ISSUER)
                .issuer(MaskinportenEnvironmentVariable.MASKINPORTEN_CLIENT_ID)
                .claim("scope", scope)
                .issueTime(Date())
                .expirationTime(LocalDateTime.now().plusSeconds(120).toDate())
                .jwtID(UUID.randomUUID().toString())
                .build(),
        )
        signedJWT.sign(RSASSASigner(rsaKey.toPrivateKey()))
        val assertion = signedJWT.serialize()

        grant(scope, assertion)
    }
}

fun HttpClientConfig<*>.maskinporten(
    scope: String,
    engine: HttpClientEngine = CIO.create(),
    block: OpenIDClientConfiguration.() -> Unit = {},
) {
    openID(maskinportenClient(engine, block).withMaskinportenAssertion(scope))
}
