package no.nav.hjelpemidler.brille.innsender

import no.nav.hjelpemidler.database.JdbcOperations
import no.nav.hjelpemidler.database.Store
import org.intellij.lang.annotations.Language

interface InnsenderStore : Store {
    fun lagreInnsender(innsender: Innsender): Innsender
    fun hentInnsender(fnrInnsender: String): Innsender?
}

class InnsenderStorePostgres(private val tx: JdbcOperations) : InnsenderStore {
    override fun lagreInnsender(innsender: Innsender): Innsender {
        @Language("PostgreSQL")
        val sql = """
            INSERT INTO innsender_v1 (fnr_innsender, godtatt)
            VALUES (:fnr_innsender, :godtatt)
        """.trimIndent()
        tx.update(
            sql,
            mapOf(
                "fnr_innsender" to innsender.fnrInnsender,
                "godtatt" to innsender.godtatt,
            ),
        ).expect(1)
        return innsender
    }

    override fun hentInnsender(fnrInnsender: String): Innsender? {
        @Language("PostgreSQL")
        val sql = """
            SELECT fnr_innsender, godtatt, opprettet
            FROM innsender_v1
            WHERE fnr_innsender = :fnr_innsender
        """.trimIndent()
        return tx.singleOrNull(sql, mapOf("fnr_innsender" to fnrInnsender)) { row ->
            Innsender(
                fnrInnsender = row.string("fnr_innsender"),
                godtatt = row.boolean("godtatt"),
                opprettet = row.localDateTime("opprettet"),
            )
        }
    }
}
