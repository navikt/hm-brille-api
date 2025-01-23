package no.nav.hjelpemidler.brille.joarkref

import no.nav.hjelpemidler.database.JdbcOperations
import no.nav.hjelpemidler.database.Store
import no.nav.hjelpemidler.database.jsonOrNull
import no.nav.hjelpemidler.database.pgJsonbOf
import org.intellij.lang.annotations.Language

interface JoarkrefStore : Store {
    fun lagreJoarkRef(vedtakId: Long, joarkRef: Long, dokumentIder: List<String>)
    fun hentJoarkRef(vedtakId: Long): Pair<Long, List<String>>?
}

class JoarkrefStorePostgres(private val tx: JdbcOperations) : JoarkrefStore {
    override fun lagreJoarkRef(vedtakId: Long, joarkRef: Long, dokumentIder: List<String>) {
        @Language("PostgreSQL")
        val sql = """
            INSERT INTO joarkref_v1 (vedtak_id, joark_ref, dokument_ider)
            VALUES (:vedtakId, :joarkRef, :dokumentIder)
            ON CONFLICT DO NOTHING
        """.trimIndent()

        tx.update(
            sql,
            mapOf(
                "vedtakId" to vedtakId,
                "joarkRef" to joarkRef,
                "dokumentIder" to pgJsonbOf(dokumentIder),
            ),
        )
    }

    override fun hentJoarkRef(vedtakId: Long): Pair<Long, List<String>>? {
        @Language("PostgreSQL")
        val sql = """
            SELECT joark_ref, dokument_ider FROM joarkref_v1 WHERE vedtak_id = :vedtakId
        """.trimIndent()

        return tx.singleOrNull(
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
