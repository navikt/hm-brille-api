package no.nav.hjelpemidler.brille.tilgang

import io.ktor.server.auth.Principal
import java.util.UUID

sealed interface UserPrincipal : Principal {
    fun kanBehandleKode6Og7(): Boolean = when {
        this is AzureAd.Systembruker -> true
        else -> false
    }

    sealed interface TokenX : UserPrincipal {
        data class Bruker(
            val fnr: String,
        ) : TokenX
    }

    sealed interface AzureAd : UserPrincipal {
        val objectId: UUID

        data class Systembruker(
            override val objectId: UUID,
        ) : AzureAd

        data class Administrator(
            override val objectId: UUID,
            val email: String,
            val name: String,
        ) : AzureAd
    }

    object Ingen : UserPrincipal
}
