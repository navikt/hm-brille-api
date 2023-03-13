package no.nav.hjelpemidler.brille

import com.auth0.jwk.JwkProviderBuilder
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.auth.principal
import mu.KotlinLogging
import no.nav.hjelpemidler.brille.tilgang.AzureAdGroup
import no.nav.hjelpemidler.brille.tilgang.UserPrincipal
import no.nav.hjelpemidler.http.openid.AzureADEnvironmentVariable
import no.nav.hjelpemidler.http.openid.TokenXEnvironmentVariable
import java.net.URL
import java.util.UUID
import java.util.concurrent.TimeUnit

const val TOKEN_X_AUTH = "tokenX"
const val AZURE_AD_AUTH = "azureAd"

private val log = KotlinLogging.logger {}

fun Application.installAuthentication() {
    val jwkProviderTokenx = JwkProviderBuilder(URL(TokenXEnvironmentVariable.TOKEN_X_JWKS_URI))
        // cache up to 1000 JWKs for 24 hours
        .cached(1000, 24, TimeUnit.HOURS)
        // if not cached, only allow max 100 different keys per minute to be fetched from external provider
        .rateLimited(100, 1, TimeUnit.MINUTES)
        .build()

    val jwkProviderAzureAd = JwkProviderBuilder(URL(AzureADEnvironmentVariable.AZURE_OPENID_CONFIG_JWKS_URI))
        // cache up to 1000 JWKs for 24 hours
        .cached(1000, 24, TimeUnit.HOURS)
        // if not cached, only allow max 100 different keys per minute to be fetched from external provider
        .rateLimited(100, 1, TimeUnit.MINUTES)
        .build()

    authentication {
        jwt(TOKEN_X_AUTH) {
            verifier(jwkProviderTokenx, TokenXEnvironmentVariable.TOKEN_X_ISSUER) {
                withAudience(TokenXEnvironmentVariable.TOKEN_X_CLIENT_ID)
                withClaim("acr", "Level4")
            }
            validate { credential ->
                val principal = JWTPrincipal(credential.payload)
                val fnr = principal.mustGet(Configuration.tokenXProperties.userclaim)
                UserPrincipal.TokenX.Bruker(fnr = fnr)
            }
        }
        provider("local") {
            authenticate { context ->
                context.principal(UserPrincipal.TokenX.Bruker("15084300133"))
            }
        }
        jwt(AZURE_AD_AUTH) {
            verifier(jwkProviderAzureAd, AzureADEnvironmentVariable.AZURE_OPENID_CONFIG_ISSUER) {
                withAudience(AzureADEnvironmentVariable.AZURE_APP_CLIENT_ID)
            }
            validate { credential ->
                val principal = JWTPrincipal(credential.payload)
                val objectId = principal.mustGet("oid").let(UUID::fromString)
                val roles = principal.getListClaim("roles", String::class).toSet()
                val groups = AzureAdGroup.fra(principal)
                when {
                    "access_as_application" in roles -> UserPrincipal.AzureAd.Systembruker(
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
        provider("local_azuread") {
            authenticate { context ->
                context.principal(
                    UserPrincipal.AzureAd.Administrator(
                        objectId = UUID.fromString("21547b88-65da-49bf-8117-075fb40e6682"),
                        email = "example@example.com",
                        name = "E. X. Ample"
                    )
                )
            }
        }
    }
}

private fun JWTPrincipal.mustGet(name: String): String =
    checkNotNull(this[name]) {
        "'$name' mangler i token"
    }

fun ApplicationCall.extractFnr(): String {
    val fnrFromClaims = this.principal<UserPrincipal.TokenX.Bruker>()?.fnr
    if (fnrFromClaims == null || fnrFromClaims.trim().isEmpty()) {
        throw RuntimeException("Fant ikke FNR i token")
    }
    return fnrFromClaims
}

fun ApplicationCall.extractUUID(): UUID =
    principal<UserPrincipal.AzureAd>()?.objectId ?: error("Fant ikke oid i token")

fun ApplicationCall.extractEmail(): String {
    val emailFromClaims = this.principal<UserPrincipal.AzureAd.Administrator>()?.email
    if (emailFromClaims == null || emailFromClaims.trim().isEmpty()) {
        error("Fant ikke email i token")
    }
    return emailFromClaims
}

fun ApplicationCall.extractName(): String {
    val nameFromClaims = this.principal<UserPrincipal.AzureAd.Administrator>()?.name
    if (nameFromClaims == null || nameFromClaims.trim().isEmpty()) {
        error("Fant ikke navn i token")
    }
    return nameFromClaims
}
