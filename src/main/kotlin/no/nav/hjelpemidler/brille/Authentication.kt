package no.nav.hjelpemidler.brille

import com.auth0.jwk.JwkProviderBuilder
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.Principal
import io.ktor.server.auth.authentication
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.auth.principal
import mu.KotlinLogging
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
            validate { credentials ->
                UserPrincipal(credentials.payload.getClaim(Configuration.tokenXProperties.userclaim).asString())
            }
        }
        provider("local") {
            authenticate { context ->
                context.principal(UserPrincipal("15084300133"))
            }
        }
        jwt(AZURE_AD_AUTH) {
            verifier(jwkProviderAzureAd, AzureADEnvironmentVariable.AZURE_OPENID_CONFIG_ISSUER) {
                withAudience(AzureADEnvironmentVariable.AZURE_APP_CLIENT_ID)
            }
            validate { credentials ->
                UserPrincipalAdmin(
                    credentials.payload.getClaim("oid").asString()
                        ?.let { oid -> kotlin.runCatching { UUID.fromString(oid) }.getOrNull() },
                    credentials.payload.getClaim("preferred_username").asString(),
                    credentials.payload.getClaim("name").asString()
                )
            }
        }
        provider("local_azuread") {
            authenticate { context ->
                context.principal(
                    UserPrincipalAdmin(
                        UUID.fromString("21547b88-65da-49bf-8117-075fb40e6682"),
                        "example@example.com",
                        "Example some some"
                    )
                )
            }
        }
    }
}

internal class UserPrincipal(private val fnr: String) : Principal {
    fun getFnr() = fnr
}

fun ApplicationCall.extractFnr(): String {
    val fnrFromClaims = this.principal<UserPrincipal>()?.getFnr()
    if (fnrFromClaims == null || fnrFromClaims.trim().isEmpty()) {
        throw RuntimeException("Fant ikke FNR i token")
    }
    return fnrFromClaims
}

internal class UserPrincipalAdmin(private val oid: UUID?, private val email: String?, private val name: String?) :
    Principal {
    fun getUUID() = oid
    fun getEmail() = email
    fun getName() = name
}

fun ApplicationCall.extractUUID(): UUID {
    val uuidFromClaims = this.principal<UserPrincipalAdmin>()?.getUUID()
        ?: throw RuntimeException("Fant ikke oid i token")
    return uuidFromClaims
}

fun ApplicationCall.extractEmail(): String {
    val emailFromClaims = this.principal<UserPrincipalAdmin>()?.getEmail()
    if (emailFromClaims == null || emailFromClaims.trim().isEmpty()) {
        throw RuntimeException("Fant ikke email i token")
    }
    return emailFromClaims
}

fun ApplicationCall.extractName(): String {
    val nameFromClaims = this.principal<UserPrincipalAdmin>()?.getName()
    if (nameFromClaims == null || nameFromClaims.trim().isEmpty()) {
        throw RuntimeException("Fant ikke navn i token")
    }
    return nameFromClaims
}
