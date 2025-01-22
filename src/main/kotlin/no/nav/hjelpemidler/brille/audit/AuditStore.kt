package no.nav.hjelpemidler.brille.audit

import no.nav.hjelpemidler.database.JdbcOperations
import no.nav.hjelpemidler.database.Store
import org.intellij.lang.annotations.Language

interface AuditStore : Store {
    fun lagreOppslag(fnrInnlogget: String, fnrOppslag: String, oppslagBeskrivelse: String)
}

class AuditStorePostgres(private val tx: JdbcOperations) : AuditStore {
    @Language("PostgreSQL")
    private val lagreOppslagSql = """
            INSERT INTO audit_v1 (fnr_innlogget, fnr_oppslag, oppslag_beskrivelse)
            VALUES (:fnr_innlogget, :fnr_oppslag, :oppslag_beskrivelse)
    """.trimIndent()

    override fun lagreOppslag(fnrInnlogget: String, fnrOppslag: String, oppslagBeskrivelse: String) {
        tx.update(
            lagreOppslagSql,
            mapOf(
                "fnr_innlogget" to fnrInnlogget,
                "fnr_oppslag" to fnrOppslag,
                "oppslag_beskrivelse" to oppslagBeskrivelse,
            ),
        ).expect(1)
    }
}
