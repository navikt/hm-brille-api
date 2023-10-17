package no.nav.hjelpemidler.brille.audit

import kotliquery.Session
import no.nav.hjelpemidler.brille.store.Store
import no.nav.hjelpemidler.brille.store.TransactionalStore
import no.nav.hjelpemidler.brille.store.update
import org.intellij.lang.annotations.Language

interface AuditStore : Store {
    fun lagreOppslag(fnrInnlogget: String, fnrOppslag: String, oppslagBeskrivelse: String)
}

internal class AuditStorePostgres(sessionFactory: () -> Session) : AuditStore,
    TransactionalStore(sessionFactory) {

    @Language("PostgreSQL")
    private val lagreOppslagSql = """
            INSERT INTO audit_v1 (fnr_innlogget, fnr_oppslag, oppslag_beskrivelse)
            VALUES (:fnr_innlogget, :fnr_oppslag, :oppslag_beskrivelse)
    """.trimIndent()

    override fun lagreOppslag(fnrInnlogget: String, fnrOppslag: String, oppslagBeskrivelse: String) = session {
        it.update(
            lagreOppslagSql,
            mapOf(
                "fnr_innlogget" to fnrInnlogget,
                "fnr_oppslag" to fnrOppslag,
                "oppslag_beskrivelse" to oppslagBeskrivelse,
            ),
        )
            .validate()
    }
}
