package no.nav.hjelpemidler.brille.tilgang

import io.ktor.server.auth.jwt.JWTPrincipal
import no.nav.hjelpemidler.brille.Configuration
import java.util.EnumSet
import java.util.UUID

enum class AzureAdRolle(val role: String, val clientId: UUID) {
    SYSTEMBRUKER_SAKSBEHANDLING("access_as_application", Configuration.CLIENT_ID_SAKSBEHANDLING),
    SYSTEMBRUKER_BRILLE_INTEGRASJON("access_as_application", Configuration.CLIENT_ID_BRILLE_INTEGRASJON),
    SYSTEMBRUKER_AZURE_TOKEN_GENERATOR("access_as_application", Configuration.CLIENT_ID_AZURE_TOKEN_GENERATOR),
    ;

    constructor(role: String, clientId: String) : this(role, UUID.fromString(clientId))

    override fun toString(): String = "$role($clientId)"

    companion object {
        fun fra(principal: JWTPrincipal): Set<AzureAdRolle> {
            val alle = EnumSet.allOf(AzureAdRolle::class.java)

            val roles = principal.getListClaim("roles", String::class)
            val clientId = principal.getClaim("azp", UUID::class)!!

            return alle.mapNotNull {
                if (it.role in roles && clientId == it.clientId) {
                    it
                } else {
                    null
                }
            }.toSet()
        }
    }
}
