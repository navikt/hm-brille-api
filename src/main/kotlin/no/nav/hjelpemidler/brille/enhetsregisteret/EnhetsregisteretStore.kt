package no.nav.hjelpemidler.brille.enhetsregisteret

import kotlinx.coroutines.runBlocking
import kotliquery.Session
import no.nav.hjelpemidler.brille.json
import no.nav.hjelpemidler.brille.pgObjectOf
import no.nav.hjelpemidler.brille.store.Store
import no.nav.hjelpemidler.brille.store.TransactionalStore
import no.nav.hjelpemidler.brille.store.query
import no.nav.hjelpemidler.brille.store.queryList
import no.nav.hjelpemidler.brille.store.update
import org.intellij.lang.annotations.Language
import java.time.LocalDateTime

interface EnhetsregisteretStore : Store {
    fun hentEnhet(orgnr: String): Organisasjonsenhet?
    fun hentEnheter(orgnre: Set<String>): Map<String, Organisasjonsenhet>
    fun oppdaterEnheter(type: EnhetType, block: (lagreEnhet: (enhet: Organisasjonsenhet) -> Unit) -> Unit)
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
        // Bare tillat orgnr-strenger som er rene tall siden (11 sifre) vi pakker argumentet inn i sql-strengen direkte (kan ikke bruke prepared statement)
        val orgnre = orgnre.filter { it.toIntOrNull() != null && it.count() == 11 }

        val sql = "SELECT data FROM enhetsregisteret_v1 WHERE orgnr IN ({ORGNRE})".replace("{ORGNRE}", orgnre.joinToString(", ") { "'$it'" })
        it.queryList(sql, emptyMap()) { row ->
            row.json<Organisasjonsenhet>("data")
        }.groupBy { it.orgnr }.mapValues { it.value.first() }
    }

    override fun oppdaterEnheter(type: EnhetType, block: (lagreEnhet: (enhet: Organisasjonsenhet) -> Unit) -> Unit) = transaction {
        // Lagre alle nye enheter i database tabellen
        val opprettet = LocalDateTime.now()
        runBlocking {
            block { enhet ->
                // Lagre enhet
                @Language("PostgreSQL")
                val sql = """
                    INSERT INTO enhetsregisteret_v1 (orgnr, opprettet, type, data)
                    VALUES (:orgnr, :opprettet, :type, :data)
                """.trimIndent()

                it.update(sql, mapOf(
                    "orgnr" to enhet.orgnr,
                    "opprettet" to opprettet,
                    "type" to type.name,
                    "data" to pgObjectOf(enhet),
                )).validate()
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
    UNDERENHET
    ;
}
