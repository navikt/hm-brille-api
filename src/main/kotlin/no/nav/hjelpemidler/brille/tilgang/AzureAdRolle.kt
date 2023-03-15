package no.nav.hjelpemidler.brille.tilgang

import io.ktor.server.auth.jwt.JWTPrincipal
import java.util.EnumSet

enum class AzureAdRolle(val id: String) {
    SYSTEMBRUKER("access_as_application"),
    ;

    override fun toString(): String = id

    companion object {
        fun fra(principal: JWTPrincipal): Set<AzureAdRolle> {
            val alle = EnumSet.allOf(AzureAdRolle::class.java)
            return principal.getListClaim("roles", String::class)
                .mapNotNull { id ->
                    alle.find {
                        id == it.id
                    }
                }
                .toSet()
        }
    }
}
