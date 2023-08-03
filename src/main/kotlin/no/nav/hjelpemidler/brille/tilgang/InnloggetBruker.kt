package no.nav.hjelpemidler.brille.tilgang

import io.ktor.server.auth.Principal
import java.util.UUID

sealed interface InnloggetBruker : Principal {
    fun kanBehandlePersonerMedAdressebeskyttelse(): Boolean = false

    sealed interface TokenX : InnloggetBruker {
        data class Bruker(
            val fnr: String,
        ) : TokenX
    }

    sealed interface AzureAd : InnloggetBruker {
        val objectId: UUID

        data class SystembrukerSaksbehandling(
            override val objectId: UUID,
        ) : AzureAd {
            override fun kanBehandlePersonerMedAdressebeskyttelse(): Boolean = true
        }

        data class SystembrukerBrilleIntegrasjon(
            override val objectId: UUID,
        ) : AzureAd {
            override fun kanBehandlePersonerMedAdressebeskyttelse(): Boolean = false
        }

        data class Administrator(
            override val objectId: UUID,
            val email: String,
            val name: String,
            val navIdent: String,
        ) : AzureAd
    }

    object Ingen : InnloggetBruker
}
