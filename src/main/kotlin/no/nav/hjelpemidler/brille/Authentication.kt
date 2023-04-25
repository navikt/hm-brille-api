package no.nav.hjelpemidler.brille

import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.authentication
import io.ktor.server.auth.principal
import no.nav.hjelpemidler.brille.tilgang.AzureAdGruppe
import no.nav.hjelpemidler.brille.tilgang.AzureAdRolle
import no.nav.hjelpemidler.brille.tilgang.InnloggetBruker
import no.nav.hjelpemidler.brille.tilgang.azureAdProvider
import no.nav.hjelpemidler.brille.tilgang.tokenXProvider
import no.nav.hjelpemidler.brille.tilgang.withAnyGroupClaim
import no.nav.hjelpemidler.brille.tilgang.withRoleClaim
import java.util.UUID

object AuthenticationProvider {
    const val TOKEN_X = "TOKEN_X"
    const val TOKEN_X_LOCAL = "TOKEN_X_LOCAL"
    const val AZURE_AD_BRILLEADMIN_BRUKERE = "AZURE_AD_BRILLEADMIN_BRUKERE"
    const val AZURE_AD_BRILLEADMIN_BRUKERE_LOCAL = "AZURE_AD_BRILLEADMIN_BRUKERE_LOCAL"
    const val AZURE_AD_SYSTEMBRUKER = "AZURE_AD_SYSTEMBRUKER"
}

fun Application.installAuthentication() {
    authentication {
        tokenXProvider(AuthenticationProvider.TOKEN_X)
        azureAdProvider(AuthenticationProvider.AZURE_AD_BRILLEADMIN_BRUKERE) {
            withAnyGroupClaim(AzureAdGruppe.TEAMDIGIHOT, AzureAdGruppe.BRILLEADMIN_BRUKERE)
        }
        azureAdProvider(AuthenticationProvider.AZURE_AD_SYSTEMBRUKER) {
            withRoleClaim(AzureAdRolle.SYSTEMBRUKER)
        }
        provider(AuthenticationProvider.TOKEN_X_LOCAL) {
            authenticate { context ->
                context.principal(InnloggetBruker.TokenX.Bruker("15084300133"))
            }
        }
        provider(AuthenticationProvider.AZURE_AD_BRILLEADMIN_BRUKERE_LOCAL) {
            authenticate { context ->
                context.principal(
                    InnloggetBruker.AzureAd.Administrator(
                        objectId = UUID.fromString("21547b88-65da-49bf-8117-075fb40e6682"),
                        email = "example@example.com",
                        name = "E. X. Ample",
                        navIdent = "X123456",
                    )
                )
            }
        }
    }
}

fun ApplicationCall.extractFnr(): String {
    val fnrFromClaims = this.principal<InnloggetBruker.TokenX.Bruker>()?.fnr
    if (fnrFromClaims == null || fnrFromClaims.trim().isEmpty()) {
        throw RuntimeException("Fant ikke FNR i token")
    }
    return fnrFromClaims
}

fun ApplicationCall.extractUUID(): UUID =
    principal<InnloggetBruker.AzureAd>()?.objectId ?: error("Fant ikke oid i token")

fun ApplicationCall.extractEmail(): String {
    val emailFromClaims = this.principal<InnloggetBruker.AzureAd.Administrator>()?.email
    if (emailFromClaims == null || emailFromClaims.trim().isEmpty()) {
        error("Fant ikke email i token")
    }
    return emailFromClaims
}

fun ApplicationCall.extractName(): String {
    val nameFromClaims = this.principal<InnloggetBruker.AzureAd.Administrator>()?.name
    if (nameFromClaims == null || nameFromClaims.trim().isEmpty()) {
        error("Fant ikke navn i token")
    }
    return nameFromClaims
}

fun ApplicationCall.extractNavIdent(): String {
    val navIdentFromClaims = this.principal<InnloggetBruker.AzureAd.Administrator>()?.navIdent
    if (navIdentFromClaims == null || navIdentFromClaims.trim().isEmpty()) {
        error("Fant ikke navIdent i token")
    }
    return navIdentFromClaims
}
