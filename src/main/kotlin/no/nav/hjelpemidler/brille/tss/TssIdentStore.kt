package no.nav.hjelpemidler.brille.tss

import kotliquery.Session
import no.nav.hjelpemidler.brille.store.Store
import no.nav.hjelpemidler.brille.store.TransactionalStore
import no.nav.hjelpemidler.brille.store.query
import no.nav.hjelpemidler.brille.store.update
import org.intellij.lang.annotations.Language

interface TssIdentStore : Store {
    fun hentTssIdent(orgnr: String): String?
    fun settTssIdent(orgnr: String, tssIdent: String)
    fun glemEksisterendeTssIdent(orgnr: String)
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
                "tssIdent" to tssIdent
            )
        ).validate()
    }

    override fun glemEksisterendeTssIdent(orgnr: String) = session {
        @Language("PostgreSQL")
        val sql = "DELETE FROM tssident_v1 WHERE orgnr = :orgnr"

        it.update(
            sql,
            mapOf(
                "orgnr" to orgnr,
            )
        )

        Unit
    }
}
