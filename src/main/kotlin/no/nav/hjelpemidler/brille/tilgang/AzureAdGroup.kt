package no.nav.hjelpemidler.brille.tilgang

import io.ktor.server.auth.jwt.JWTPrincipal
import no.nav.hjelpemidler.brille.Configuration
import java.util.EnumSet
import java.util.UUID

enum class AzureAdGroup(val objectId: UUID) {
    /**
     * AD: teamdigihot
     */
    TEAMDIGIHOT(Configuration.GRUPPE_TEAMDIGIHOT),

    /**
     * AD: 0000-GA-brilleadmin-brukere
     */
    BRILLEADMIN_BRUKERE(Configuration.GRUPPE_BRILLEADMIN_BRUKERE),
    ;

    constructor(objectId: String) : this(UUID.fromString(objectId))

    override fun toString(): String = objectId.toString()

    companion object {
        fun fra(principal: JWTPrincipal): Set<AzureAdGroup> {
            val alle = EnumSet.allOf(AzureAdGroup::class.java)
            return principal.getListClaim("groups", UUID::class)
                .mapNotNull { id ->
                    alle.find {
                        id == it.objectId
                    }
                }
                .toSet()
        }
    }
}
