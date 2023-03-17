package no.nav.hjelpemidler.brille.tilgang

import com.auth0.jwk.JwkProviderBuilder
import com.auth0.jwt.interfaces.Verification
import com.google.common.collect.Sets
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

fun ApplicationCall.innloggetBruker(): InnloggetBruker =
    principal() ?: InnloggetBruker.Ingen

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
            InnloggetBruker.TokenX.Bruker(fnr = fnr)
        }
    }
}

fun AuthenticationConfig.azureAdProvider(
    name: String,
    grupperSomKreves: Set<AzureAdGruppe> = emptySet(),
    block: Verification.() -> Unit = {},
) {
    jwt(name) {
        verifier(jwkProviderAzureAd, AzureADEnvironmentVariable.AZURE_OPENID_CONFIG_ISSUER) {
            withAudience(AzureADEnvironmentVariable.AZURE_APP_CLIENT_ID)
            block()
        }
        validate { credential ->
            val principal = JWTPrincipal(credential.payload)
            val objectId = principal.mustGet("oid").let(UUID::fromString)
            val roller = AzureAdRolle.fra(principal)
            val grupper = AzureAdGruppe.fra(principal)
            when {
                grupper.inneholderIngenAv(grupperSomKreves) -> null

                AzureAdRolle.SYSTEMBRUKER in roller -> InnloggetBruker.AzureAd.Systembruker(
                    objectId = objectId,
                )

                AzureAdGruppe.BRILLEADMIN_BRUKERE in grupper -> InnloggetBruker.AzureAd.Administrator(
                    objectId = objectId,
                    email = principal.mustGet("preferred_username"),
                    name = principal.mustGet("name"),
                )

                else -> null
            }
        }
    }
}

fun <T : Any> Set<T>.inneholderIngenAv(other: Set<T>): Boolean =
    other.isEmpty() || Sets.intersection(this, other).isEmpty()

fun Verification.withAnyGroupClaim(vararg grupper: AzureAdGruppe) {
    // fixme -> kan implementeres når ktor-server-auth-jwt bumper java-jwt til 4.*, da kan man lage egne verifikasjoner med predikater, foreløpig sjekker vi dette med egen kode
    // withArrayClaim("groups", group.toString())
}

fun Verification.withRoleClaim(rolle: AzureAdRolle) {
    withArrayClaim("roles", rolle.toString())
}
