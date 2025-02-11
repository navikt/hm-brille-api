package no.nav.hjelpemidler.brille.tilgang

import com.auth0.jwk.JwkProviderBuilder
import com.auth0.jwt.exceptions.JWTDecodeException
import com.auth0.jwt.interfaces.Verification
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.AuthenticationConfig
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.auth.principal
import no.nav.hjelpemidler.configuration.ClusterEnvironment
import no.nav.hjelpemidler.configuration.EntraIDEnvironmentVariable
import no.nav.hjelpemidler.configuration.Environment
import no.nav.hjelpemidler.configuration.TokenXEnvironmentVariable
import no.nav.hjelpemidler.logging.secureWarn
import java.net.URI
import java.util.UUID
import java.util.concurrent.TimeUnit

private val log = KotlinLogging.logger { }

fun ApplicationCall.innloggetBruker(): InnloggetBruker =
    principal() ?: InnloggetBruker.Ingen

private fun JWTPrincipal.mustGet(name: String): String =
    checkNotNull(this[name]) {
        "'$name' mangler i token"
    }

private val jwkProviderTokenX =
    JwkProviderBuilder(URI(TokenXEnvironmentVariable.TOKEN_X_JWKS_URI).toURL())
        // cache up to 1000 JWKs for 24 hours
        .cached(1000, 24, TimeUnit.HOURS)
        // if not cached, only allow max 100 different keys per minute to be fetched from external provider
        .rateLimited(100, 1, TimeUnit.MINUTES)
        .build()

private val jwkProviderAzureAd =
    JwkProviderBuilder(URI(EntraIDEnvironmentVariable.AZURE_OPENID_CONFIG_JWKS_URI).toURL())
        // cache up to 1000 JWKs for 24 hours
        .cached(1000, 24, TimeUnit.HOURS)
        // if not cached, only allow max 100 different keys per minute to be fetched from external provider
        .rateLimited(100, 1, TimeUnit.MINUTES)
        .build()

fun AuthenticationConfig.tokenXProvider(name: String) {
    val fnrClaim = if (Environment.current is ClusterEnvironment) {
        "pid"
    } else {
        "sub"
    }
    jwt(name) {
        verifier(jwkProviderTokenX, TokenXEnvironmentVariable.TOKEN_X_ISSUER) {
            withAudience(TokenXEnvironmentVariable.TOKEN_X_CLIENT_ID)
            withClaim("acr", "Level4")
        }
        validate { credential ->
            val principal = JWTPrincipal(credential.payload)
            val fnr = principal.mustGet(fnrClaim)
            InnloggetBruker.TokenX.Bruker(fnr = fnr)
        }
    }
}

fun AuthenticationConfig.azureAdProvider(
    name: String,
    block: Verification.() -> Unit = {},
) {
    jwt(name) {
        verifier(jwkProviderAzureAd, EntraIDEnvironmentVariable.AZURE_OPENID_CONFIG_ISSUER) {
            withAudience(EntraIDEnvironmentVariable.AZURE_APP_CLIENT_ID)
            block()
        }
        validate { credential ->
            val principal = JWTPrincipal(credential.payload)
            val objectId = principal.mustGet("oid").let(UUID::fromString)
            val roller = AzureAdRolle.fra(principal)
            val grupper = AzureAdGruppe.fra(principal)
            when {
                AzureAdRolle.SYSTEMBRUKER_SAKSBEHANDLING in roller || AzureAdRolle.SYSTEMBRUKER_AZURE_TOKEN_GENERATOR in roller ->
                    InnloggetBruker.AzureAd.SystembrukerSaksbehandling(
                        objectId = objectId,
                    )

                AzureAdRolle.SYSTEMBRUKER_BRILLE_INTEGRASJON in roller ->
                    InnloggetBruker.AzureAd.SystembrukerBrilleIntegrasjon(
                        objectId = objectId,
                    )

                AzureAdGruppe.TEAMDIGIHOT in grupper || AzureAdGruppe.BRILLEADMIN_BRUKERE in grupper ->
                    InnloggetBruker.AzureAd.Administrator(
                        objectId = objectId,
                        email = principal.mustGet("preferred_username"),
                        name = principal.mustGet("name"),
                        navIdent = principal.mustGet("NAVident"),
                    )

                else -> {
                    log.secureWarn {
                        "Validering av Azure AD-bruker feilet, objectId: $objectId, roller: $roller, grupper: $grupper"
                    }
                    null
                }
            }
        }
    }
}

/**
 * Verifiser at token inneholder et groups claim med minst en av verdiene i [grupper].
 */
fun Verification.withAnyGroupClaim(vararg grupper: AzureAdGruppe): Verification =
    withAnyOfClaim("groups", grupper.map(AzureAdGruppe::objectId))

/**
 * Verifiser at token inneholder et roles claim som inneholder [rolle] og et tilhørende azp claim.
 */
fun Verification.withRoleAndClientIdClaim(rolle: AzureAdRolle) {
    withArrayClaim("roles", rolle.role)
    withClaim("azp", rolle.clientId.toString())
}

/**
 * Verifiser at token inneholder et roles claim med minst en av verdiene i [roller] og et tilhørende azp claim.
 */
fun Verification.withAnyRoleAndClientIdClaim(vararg roller: AzureAdRolle) {
    withAnyOfClaim("roles", roller.map(AzureAdRolle::role))
    withClaim("azp") { claim, _ ->
        try {
            claim.`as`(UUID::class.java) in roller.map(AzureAdRolle::clientId)
        } catch (e: JWTDecodeException) {
            false
        }
    }
}

/**
 * Verifiser at token inneholder et array claim med minst en av verdiene i [items].
 */
inline fun <reified T> Verification.withAnyOfClaim(name: String, items: Collection<T>): Verification =
    withClaim(name) { claim, _ ->
        try {
            claim.asList(T::class.java)?.any { it in items } == true
        } catch (e: JWTDecodeException) {
            false
        }
    }
