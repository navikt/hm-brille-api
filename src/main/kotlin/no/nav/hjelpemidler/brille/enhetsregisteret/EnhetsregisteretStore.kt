package no.nav.hjelpemidler.brille.enhetsregisteret

import kotlinx.coroutines.runBlocking
import kotliquery.Session
import no.nav.hjelpemidler.brille.json
import no.nav.hjelpemidler.brille.pgObjectOf
import no.nav.hjelpemidler.brille.store.Store
import no.nav.hjelpemidler.brille.store.StoreException
import no.nav.hjelpemidler.brille.store.TransactionalStore
import no.nav.hjelpemidler.brille.store.query
import no.nav.hjelpemidler.brille.store.queryList
import no.nav.hjelpemidler.brille.store.update
import org.intellij.lang.annotations.Language
import java.time.LocalDateTime

interface EnhetsregisteretStore : Store {
    fun hentEnhet(orgnr: String): Organisasjonsenhet?
    fun hentEnheter(orgnre: Set<String>): Map<String, Organisasjonsenhet>
    fun oppdaterEnheter(block: (lagre: (type: EnhetType, enhetChunk: List<Organisasjonsenhet>) -> Unit) -> Unit)
    fun sistOppdatert(): LocalDateTime?
}

class EnhetsregisteretStorePostgres(sessionFactory: () -> Session) : EnhetsregisteretStore, TransactionalStore(sessionFactory) {
    override fun hentEnhet(orgnr: String): Organisasjonsenhet? = session {
        val sql = "SELECT data FROM enhetsregisteret_v1 WHERE orgnr = :orgnr"
        it.query(sql, mapOf("orgnr" to orgnr)) { row ->
            row.json<Organisasjonsenhet>("data")
        }
    }

    override fun hentEnheter(orgnre: Set<String>): Map<String, Organisasjonsenhet> = session {
        // Bare tillat orgnr-strenger som er rene tall siden vi pakker argumentet inn i sql-strengen direkte (kan ikke bruke prepared statement)
        val orgnreFiltrert = orgnre.filter { it.toIntOrNull() != null }
        val results = mutableMapOf<String, Organisasjonsenhet>()
        // Spør om maks 50 orgnre om gangen
        for (chunk in orgnreFiltrert.chunked(50)) {
            val sql = "SELECT data FROM enhetsregisteret_v1 WHERE orgnr IN ({ORGNRE})".replace("{ORGNRE}", chunk.joinToString(", ") { "'$it'" })
            results.putAll(
                it.queryList(sql, emptyMap()) { row ->
                    row.json<Organisasjonsenhet>("data")
                }.groupBy { it.orgnr }.mapValues { it.value.first() },
            )
        }
        results
    }

    override fun oppdaterEnheter(block: (lagre: (type: EnhetType, enhetChunk: List<Organisasjonsenhet>) -> Unit) -> Unit) = transaction {
        // Lagre alle nye enheter i database tabellen
        val opprettet = LocalDateTime.now()
        runBlocking {
            block { type, enhetChunk ->
                // Lagre enhet
                @Language("PostgreSQL")
                val sql = """
                    INSERT INTO enhetsregisteret_v1 (orgnr, opprettet, type, data)
                    VALUES (:orgnr, :opprettet, :type, :data)
                """.trimIndent()

                // Batch-oppdater rader i databasen med en enkelt PreparedStatement
                val rowsUpdated = it.batchPreparedNamedStatement(
                    sql,
                    enhetChunk.map { enhet ->
                        mapOf(
                            "orgnr" to enhet.orgnr,
                            "opprettet" to opprettet,
                            "type" to type.name,
                            "data" to pgObjectOf(enhet),
                        )
                    },
                )
                if (rowsUpdated.count { it > 0 } != enhetChunk.count()) throw StoreException("en eller flere rowsUpdated i batch var 0")
            }
        }

        // Slett de gamle enhetene som nå er utdaterte
        it.update("DELETE FROM enhetsregisteret_v1 WHERE opprettet <> :opprettet", mapOf("opprettet" to opprettet))

        return@transaction Unit
    }

    override fun sistOppdatert(): LocalDateTime? = transaction {
        it.query("SELECT opprettet FROM enhetsregisteret_v1 ORDER BY opprettet DESC LIMIT 1", emptyMap()) {
            it.localDateTime("opprettet")
        }
    }
}

enum class EnhetType {
    HOVEDENHET,
    UNDERENHET,
}
