package no.nav.hjelpemidler.brille.audit

import no.nav.hjelpemidler.brille.db.DatabaseContext
import no.nav.hjelpemidler.brille.db.transaction

/**
 * Lagrer oppslag av sensitiv informasjon for å muliggjøre kontroll for misbruk av tjenesten.
 */
class AuditService(val databaseContext: DatabaseContext) {
    suspend fun lagreOppslag(fnrInnlogget: String, fnrOppslag: String, oppslagBeskrivelse: String) {
        transaction(databaseContext) { ctx ->
            ctx.auditStore.lagreOppslag(
                fnrInnlogget = fnrInnlogget,
                fnrOppslag = fnrOppslag,
                oppslagBeskrivelse = oppslagBeskrivelse
            )
        }
    }
}
