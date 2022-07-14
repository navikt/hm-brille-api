package no.nav.hjelpemidler.brille.audit

/**
 * Lagrer oppslag av sensitiv informasjon for å muliggjøre kontroll for misbruk av tjenesten.
 */
class AuditService(private val auditStore: AuditStore) {
    fun lagreOppslag(fnrInnlogget: String, fnrOppslag: String, oppslagBeskrivelse: String) {
        auditStore.lagreOppslag(
            fnrInnlogget = fnrInnlogget,
            fnrOppslag = fnrOppslag,
            oppslagBeskrivelse = oppslagBeskrivelse
        )
    }
}
