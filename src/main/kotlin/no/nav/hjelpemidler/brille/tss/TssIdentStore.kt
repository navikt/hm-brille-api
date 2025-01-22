package no.nav.hjelpemidler.brille.tss

import no.nav.hjelpemidler.database.JdbcOperations
import no.nav.hjelpemidler.database.Store
import org.intellij.lang.annotations.Language

interface TssIdentStore : Store {
    fun hentTssIdent(orgnr: String): String?
    fun settTssIdent(orgnr: String, tssIdent: String)
    fun glemEksisterendeTssIdent(orgnr: String)
    fun hentAlleOrgnrSomManglerTssIdent(): List<String>
}

class TssIdentStorePostgres(private val tx: JdbcOperations) : TssIdentStore {
    override fun hentTssIdent(orgnr: String): String? {
        return tx.singleOrNull(
            "SELECT tss_ident FROM tssident_v1 WHERE orgnr = :orgnr",
            mapOf("orgnr" to orgnr),
        ) {
            it.string("tss_ident")
        }
    }

    override fun settTssIdent(orgnr: String, tssIdent: String) {
        @Language("PostgreSQL")
        val sql = """
            INSERT INTO tssident_v1 (orgnr, tss_ident, opprettet)
            VALUES (:orgnr, :tssIdent, NOW())
            ON CONFLICT (orgnr)
            DO UPDATE SET tss_ident = :tssIdent, opprettet = NOW()
        """.trimIndent()

        tx.update(
            sql,
            mapOf(
                "orgnr" to orgnr,
                "tssIdent" to tssIdent,
            ),
        ).expect(1)
    }

    override fun glemEksisterendeTssIdent(orgnr: String) {
        @Language("PostgreSQL")
        val sql = "DELETE FROM tssident_v1 WHERE orgnr = :orgnr"

        tx.update(
            sql,
            mapOf("orgnr" to orgnr),
        )
    }

    override fun hentAlleOrgnrSomManglerTssIdent(): List<String> {
        @Language("PostgreSQL")
        val sql = """
            SELECT orgnr
            FROM virksomhet_v1
            WHERE orgnr NOT IN (SELECT orgnr FROM tssident_v1) AND aktiv
            ORDER BY orgnr ASC
        """.trimIndent()

        return tx.list(sql, emptyMap()) { row ->
            row.string("orgnr")
        }.toSet().toList()
    }
}
