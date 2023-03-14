package no.nav.hjelpemidler.brille.tilgang

import io.ktor.server.auth.jwt.JWTPrincipal
import java.util.EnumSet

enum class AzureAdRole(val id: String) {
    SYSTEMBRUKER("access_as_application"),
    ;

    override fun toString(): String = id

    companion object {
        fun fra(principal: JWTPrincipal): Set<AzureAdRole> {
            val alle = EnumSet.allOf(AzureAdRole::class.java)
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
