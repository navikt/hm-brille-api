package no.nav.hjelpemidler.brille.innsender

import no.nav.hjelpemidler.brille.store.Store
import no.nav.hjelpemidler.brille.store.query
import no.nav.hjelpemidler.brille.store.update
import org.intellij.lang.annotations.Language
import javax.sql.DataSource

interface InnsenderStore : Store {
    fun lagreInnsender(innsender: Innsender): Innsender
    fun hentInnsender(fnrInnsender: String): Innsender?
}

class InnsenderStorePostgres(private val ds: DataSource) : InnsenderStore {
    override fun lagreInnsender(innsender: Innsender): Innsender {
        @Language("PostgreSQL")
        val sql = """
            INSERT INTO innsender_v1 (fnr_innsender, godtatt)
            VALUES (:fnr_innsender, :godtatt)
        """.trimIndent()
        ds.update(
            sql,
            mapOf(
                "fnr_innsender" to innsender.fnrInnsender,
                "godtatt" to innsender.godtatt
            )
        ).validate()
        return innsender
    }

    override fun hentInnsender(fnrInnsender: String): Innsender? {
        @Language("PostgreSQL")
        val sql = """
            SELECT fnr_innsender, godtatt, opprettet
            FROM innsender_v1
            WHERE fnr_innsender = :fnr_innsender
        """.trimIndent()
        return ds.query(sql, mapOf("fnr_innsender" to fnrInnsender)) { row ->
            Innsender(
                fnrInnsender = row.string("fnr_innsender"),
                godtatt = row.boolean("godtatt"),
                opprettet = row.localDateTime("opprettet")
            )
        }
    }
}
