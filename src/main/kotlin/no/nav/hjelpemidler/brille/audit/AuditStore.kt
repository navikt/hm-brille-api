package no.nav.hjelpemidler.brille.audit

import no.nav.hjelpemidler.brille.update
import org.intellij.lang.annotations.Language
import javax.sql.DataSource

interface AuditStore {
    fun lagreOppslag(fnrInnlogget: String, fnrOppslag: String, oppslagBeskrivelse: String)
}

internal class AuditStorePostgres(private val ds: DataSource) : AuditStore {

    @Language("PostgreSQL")
    private val lagreOppslagSql = """
            INSERT INTO audit_v1 (fnr_innlogget, fnr_oppslag, oppslag_beskrivelse)
            VALUES (:fnr_innlogget, :fnr_oppslag, :oppslag_beskrivelse)
    """.trimIndent()

    override fun lagreOppslag(fnrInnlogget: String, fnrOppslag: String, oppslagBeskrivelse: String) {
        val rowsUpdated = ds.update(
            lagreOppslagSql,
            mapOf(
                "fnr_innlogget" to fnrInnlogget,
                "fnr_oppslag" to fnrOppslag,
                "oppslag_beskrivelse" to oppslagBeskrivelse
            )
        )
        if (rowsUpdated == 0) {
            throw RuntimeException("Lagring av oppslag til audittabell feilet (rowsUpdated == 0)")
        }
    }
}
