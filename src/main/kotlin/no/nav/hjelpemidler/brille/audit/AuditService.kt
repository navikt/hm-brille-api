package no.nav.hjelpemidler.brille.audit

class AuditService(private val auditStore: AuditStore) {

    fun lagreOppslag(fnrInnlogget: String, fnrOppslag: String) {
        auditStore.lagreOppslag(
            fnrInnlogget = fnrInnlogget,
            fnrOppslag = fnrOppslag
        )
    }
}
