package no.nav.hjelpemidler.brille.enhetsregisteret

import no.nav.hjelpemidler.database.JdbcOperations
import no.nav.hjelpemidler.database.Store
import no.nav.hjelpemidler.database.pgJsonbOf
import org.intellij.lang.annotations.Language
import java.sql.Statement
import java.time.LocalDateTime

interface EnhetsregisteretStore : Store {
    fun hentEnhet(orgnr: String): Organisasjonsenhet?
    fun hentEnheter(orgnre: Set<String>): Map<String, Organisasjonsenhet>
    fun oppdaterEnheter(block: (lagre: (type: EnhetType, enhetChunk: List<Organisasjonsenhet>) -> Unit) -> Unit)
    fun sistOppdatert(): LocalDateTime?
}

class EnhetsregisteretStorePostgres(private val tx: JdbcOperations) : EnhetsregisteretStore {
    override fun hentEnhet(orgnr: String): Organisasjonsenhet? {
        val sql = "SELECT data FROM enhetsregisteret_v1 WHERE orgnr = :orgnr"
        return tx.singleOrNull(sql, mapOf("orgnr" to orgnr)) { row ->
            row.json<Organisasjonsenhet>("data")
        }
    }

    override fun hentEnheter(orgnre: Set<String>): Map<String, Organisasjonsenhet> {
        // Bare tillat orgnr-strenger som er rene tall siden vi pakker argumentet inn i sql-strengen direkte (kan ikke bruke prepared statement)
        val orgnreFiltrert = orgnre.filter { it.toIntOrNull() != null }
        val results = mutableMapOf<String, Organisasjonsenhet>()
        // Spør om maks 50 orgnre om gangen
        for (chunk in orgnreFiltrert.chunked(50)) {
            val sql = "SELECT data FROM enhetsregisteret_v1 WHERE orgnr IN ({ORGNRE})".replace(
                "{ORGNRE}",
                chunk.joinToString(", ") { "'$it'" },
            )
            results.putAll(
                tx.list(sql, emptyMap()) { row ->
                    row.json<Organisasjonsenhet>("data")
                }.groupBy { it.orgnr }.mapValues { it.value.first() },
            )
        }
        return results
    }

    override fun oppdaterEnheter(block: (lagre: (type: EnhetType, enhetChunk: List<Organisasjonsenhet>) -> Unit) -> Unit) {
        // Lagre alle nye enheter i database tabellen
        val opprettet = LocalDateTime.now()
        block { type, enhetChunk ->
            // Lagre enhet
            @Language("PostgreSQL")
            val sql = """
                INSERT INTO enhetsregisteret_v1 (orgnr, opprettet, type, data)
                VALUES (:orgnr, :opprettet, :type, :data)
            """.trimIndent()

            // Batch-oppdater rader i databasen med en enkelt PreparedStatement
            val rowsUpdated = tx.batch(
                sql,
                enhetChunk.map { enhet ->
                    mapOf(
                        "orgnr" to enhet.orgnr,
                        "opprettet" to opprettet,
                        "type" to type.name,
                        "data" to pgJsonbOf(enhet),
                    )
                },
            )
            // NB! reWriteBatchedInserts=true gir Statement.SUCCESS_NO_INFO i noen tilfeller
            if (rowsUpdated.count { it > 0 || it == Statement.SUCCESS_NO_INFO } != enhetChunk.count()) error("En eller flere batcher feilet")
        }

        // Slett de gamle enhetene som nå er utdaterte
        tx.execute("DELETE FROM enhetsregisteret_v1 WHERE opprettet <> :opprettet", mapOf("opprettet" to opprettet))
    }

    override fun sistOppdatert(): LocalDateTime? {
        return tx.singleOrNull("SELECT MAX(opprettet) FROM enhetsregisteret_v1") {
            it.localDateTimeOrNull(1)
        }
    }
}

enum class EnhetType {
    HOVEDENHET,
    UNDERENHET,
}
