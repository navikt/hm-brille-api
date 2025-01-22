package no.nav.hjelpemidler.brille.test

import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.interfaces.DecodedJWT
import com.auth0.jwt.interfaces.Verification
import com.nimbusds.jose.JOSEObjectType
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.JWSSigner
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.jwk.KeyUse
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
import com.nimbusds.jwt.JWT
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import no.nav.hjelpemidler.brille.tilgang.AzureAdGruppe
import no.nav.hjelpemidler.brille.tilgang.AzureAdRolle
import no.nav.hjelpemidler.configuration.EntraIDEnvironmentVariable
import java.security.interfaces.RSAPrivateKey
import java.util.Date
import java.util.UUID
import com.auth0.jwt.JWT as Jwt

val testRSAKey: RSAKey =
    RSAKeyGenerator(2048)
        .keyID(UUID.randomUUID().toString())
        .keyUse(KeyUse.SIGNATURE)
        .issueTime(Date())
        .generate()

private val testRSAPrivateKey: RSAPrivateKey =
    testRSAKey.toRSAPrivateKey()

private val testJWSSigner: JWSSigner =
    RSASSASigner(testRSAPrivateKey)

private val testAlgorithm: Algorithm =
    Algorithm.RSA256(
        testRSAKey.toRSAPublicKey(),
        testRSAPrivateKey,
    )

fun verifyToken(token: JWT, block: Verification.() -> Unit = {}): DecodedJWT =
    Jwt.require(testAlgorithm)
        .withIssuer(EntraIDEnvironmentVariable.AZURE_OPENID_CONFIG_ISSUER)
        .withAudience(EntraIDEnvironmentVariable.AZURE_APP_CLIENT_ID)
        .apply(block)
        .build()
        .verify(token.serialize())

fun JWTClaimsSet.toToken(): JWT {
    val header = JWSHeader.Builder(JWSAlgorithm.RS256)
        .type(JOSEObjectType.JWT)
        .keyID(UUID.randomUUID().toString())
        .build()
    return SignedJWT(header, this).also { token ->
        token.sign(testJWSSigner)
    }
}

fun createToken(block: JWTClaimsSet.Builder.() -> Unit = {}): JWT =
    JWTClaimsSet.Builder()
        .audience(EntraIDEnvironmentVariable.AZURE_APP_CLIENT_ID)
        .issueTime(Date())
        .issuer(EntraIDEnvironmentVariable.AZURE_OPENID_CONFIG_ISSUER)
        .claim("oid", UUID.randomUUID())
        .apply(block)
        .build()
        .toToken()

fun JWTClaimsSet.Builder.azp(value: UUID): JWTClaimsSet.Builder =
    claim("azp", value.toString())

fun JWTClaimsSet.Builder.azp(rolle: AzureAdRolle): JWTClaimsSet.Builder =
    azp(rolle.clientId)

fun JWTClaimsSet.Builder.role(rolle: AzureAdRolle): JWTClaimsSet.Builder =
    azp(rolle).roles(rolle)

fun JWTClaimsSet.Builder.roles(vararg roller: AzureAdRolle): JWTClaimsSet.Builder =
    claim("roles", roller.map(AzureAdRolle::role))

fun JWTClaimsSet.Builder.groups(vararg grupper: AzureAdGruppe): JWTClaimsSet.Builder =
    claim("groups", grupper.map(AzureAdGruppe::objectId))
