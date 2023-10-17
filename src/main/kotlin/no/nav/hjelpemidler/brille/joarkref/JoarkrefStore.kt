package no.nav.hjelpemidler.brille.joarkref

import kotliquery.Session
import no.nav.hjelpemidler.brille.jsonOrNull
import no.nav.hjelpemidler.brille.pgObjectOf
import no.nav.hjelpemidler.brille.store.Store
import no.nav.hjelpemidler.brille.store.TransactionalStore
import no.nav.hjelpemidler.brille.store.query
import no.nav.hjelpemidler.brille.store.update
import org.intellij.lang.annotations.Language

interface JoarkrefStore : Store {
    fun lagreJoarkRef(vedtakId: Long, joarkRef: Long, dokumentIder: List<String>)
    fun hentJoarkRef(vedtakId: Long): Pair<Long, List<String>>?
}

class JoarkrefStorePostgres(private val sessionFactory: () -> Session) : JoarkrefStore, TransactionalStore(sessionFactory) {
    override fun lagreJoarkRef(vedtakId: Long, joarkRef: Long, dokumentIder: List<String>) = session {
        @Language("PostgreSQL")
        val sql = """
            INSERT INTO joarkref_v1 (vedtak_id, joark_ref, dokument_ider)
            VALUES (:vedtakId, :joarkRef, :dokumentIder)
            ON CONFLICT DO NOTHING
        """.trimIndent()

        it.update(
            sql,
            mapOf(
                "vedtakId" to vedtakId,
                "joarkRef" to joarkRef,
                "dokumentIder" to pgObjectOf(dokumentIder),
            ),
        )

        Unit
    }

    override fun hentJoarkRef(vedtakId: Long) = session {
        @Language("PostgreSQL")
        val sql = """
            SELECT joark_ref, dokument_ider FROM joarkref_v1 WHERE vedtak_id = :vedtakId
        """.trimIndent()

        it.query(
            sql,
            mapOf(
                "vedtakId" to vedtakId,
            ),
        ) {
            Pair(
                it.long("joark_ref"),
                it.jsonOrNull<List<String>>("dokument_ider") ?: listOf(),
            )
        }
    }
}
