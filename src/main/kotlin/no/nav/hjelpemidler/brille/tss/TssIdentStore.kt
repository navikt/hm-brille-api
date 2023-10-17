package no.nav.hjelpemidler.brille.tss

import kotliquery.Session
import no.nav.hjelpemidler.brille.store.Store
import no.nav.hjelpemidler.brille.store.TransactionalStore
import no.nav.hjelpemidler.brille.store.query
import no.nav.hjelpemidler.brille.store.queryList
import no.nav.hjelpemidler.brille.store.update
import org.intellij.lang.annotations.Language

interface TssIdentStore : Store {
    fun hentTssIdent(orgnr: String): String?
    fun settTssIdent(orgnr: String, tssIdent: String)
    fun glemEksisterendeTssIdent(orgnr: String)
    fun hentAlleOrgnrSomManglerTssIdent(): List<String>
}

class TssIdentStorePostgres(private val sessionFactory: () -> Session) : TssIdentStore, TransactionalStore(sessionFactory) {
    override fun hentTssIdent(orgnr: String) = session {
        it.query(
            "SELECT tss_ident FROM tssident_v1 WHERE orgnr = :orgnr",
            mapOf("orgnr" to orgnr),
        ) {
            it.string("tss_ident")
        }
    }

    override fun settTssIdent(orgnr: String, tssIdent: String) = session {
        @Language("PostgreSQL")
        val sql = """
            INSERT INTO tssident_v1 (orgnr, tss_ident, opprettet)
            VALUES (:orgnr, :tssIdent, now())
            ON CONFLICT (orgnr)
            DO UPDATE SET tss_ident = :tssIdent, opprettet = NOW()
        """.trimIndent()

        it.update(
            sql,
            mapOf(
                "orgnr" to orgnr,
                "tssIdent" to tssIdent,
            ),
        ).validate()
    }

    override fun glemEksisterendeTssIdent(orgnr: String) = session {
        @Language("PostgreSQL")
        val sql = "DELETE FROM tssident_v1 WHERE orgnr = :orgnr"

        it.update(
            sql,
            mapOf(
                "orgnr" to orgnr,
            ),
        )

        Unit
    }

    override fun hentAlleOrgnrSomManglerTssIdent() = session {
        @Language("PostgreSQL")
        val sql = """
            SELECT orgnr
            FROM virksomhet_v1
            WHERE orgnr NOT IN (SELECT orgnr FROM tssident_v1) AND aktiv
            ORDER BY orgnr ASC
        """.trimIndent()

        it.queryList(sql, emptyMap()) { row ->
            row.string("orgnr")
        }.toSet().toList()
    }
}
