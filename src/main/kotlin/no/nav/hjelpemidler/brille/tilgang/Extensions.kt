package no.nav.hjelpemidler.brille.tilgang

import com.auth0.jwk.JwkProviderBuilder
import com.auth0.jwt.interfaces.Verification
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.AuthenticationConfig
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.auth.principal
import no.nav.hjelpemidler.brille.Configuration
import no.nav.hjelpemidler.http.openid.AzureADEnvironmentVariable
import no.nav.hjelpemidler.http.openid.TokenXEnvironmentVariable
import java.net.URL
import java.util.UUID
import java.util.concurrent.TimeUnit

fun ApplicationCall.currentUser(): UserPrincipal =
    principal() ?: UserPrincipal.Ingen

private fun JWTPrincipal.mustGet(name: String): String =
    checkNotNull(this[name]) {
        "'$name' mangler i token"
    }

private val jwkProviderTokenX = JwkProviderBuilder(URL(TokenXEnvironmentVariable.TOKEN_X_JWKS_URI))
    // cache up to 1000 JWKs for 24 hours
    .cached(1000, 24, TimeUnit.HOURS)
    // if not cached, only allow max 100 different keys per minute to be fetched from external provider
    .rateLimited(100, 1, TimeUnit.MINUTES)
    .build()

private val jwkProviderAzureAd = JwkProviderBuilder(URL(AzureADEnvironmentVariable.AZURE_OPENID_CONFIG_JWKS_URI))
    // cache up to 1000 JWKs for 24 hours
    .cached(1000, 24, TimeUnit.HOURS)
    // if not cached, only allow max 100 different keys per minute to be fetched from external provider
    .rateLimited(100, 1, TimeUnit.MINUTES)
    .build()

fun AuthenticationConfig.tokenXProvider(name: String) {
    jwt(name) {
        verifier(jwkProviderTokenX, TokenXEnvironmentVariable.TOKEN_X_ISSUER) {
            withAudience(TokenXEnvironmentVariable.TOKEN_X_CLIENT_ID)
            withClaim("acr", "Level4")
        }
        validate { credential ->
            val principal = JWTPrincipal(credential.payload)
            val fnr = principal.mustGet(Configuration.tokenXProperties.userclaim)
            UserPrincipal.TokenX.Bruker(fnr = fnr)
        }
    }
}

fun AuthenticationConfig.azureAdProvider(name: String, block: Verification.() -> Unit = {}) {
    jwt(name) {
        verifier(jwkProviderAzureAd, AzureADEnvironmentVariable.AZURE_OPENID_CONFIG_ISSUER) {
            withAudience(AzureADEnvironmentVariable.AZURE_APP_CLIENT_ID)
            block()
        }
        validate { credential ->
            val principal = JWTPrincipal(credential.payload)
            val objectId = principal.mustGet("oid").let(UUID::fromString)
            val roles = AzureAdRole.fra(principal)
            val groups = AzureAdGroup.fra(principal)
            when {
                AzureAdRole.SYSTEMBRUKER in roles -> UserPrincipal.AzureAd.Systembruker(
                    objectId = objectId,
                )

                AzureAdGroup.BRILLEADMIN_BRUKERE in groups -> UserPrincipal.AzureAd.Administrator(
                    objectId = objectId,
                    email = principal.mustGet("preferred_username"),
                    name = principal.mustGet("name"),
                )

                else -> null
            }
        }
    }
}

fun Verification.withGroupClaim(group: AzureAdGroup) {
    withArrayClaim("groups", group.toString())
}

fun Verification.withRoleClaim(role: AzureAdRole) {
    withArrayClaim("roles", role.toString())
}
