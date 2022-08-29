package no.nav.hjelpemidler.brille.utbetaling

import kotliquery.Row
import kotliquery.Session
import no.nav.hjelpemidler.brille.json
import no.nav.hjelpemidler.brille.pgObjectOf
import no.nav.hjelpemidler.brille.store.Store
import no.nav.hjelpemidler.brille.store.TransactionalStore
import no.nav.hjelpemidler.brille.store.query
import no.nav.hjelpemidler.brille.store.queryList
import no.nav.hjelpemidler.brille.store.update
import org.intellij.lang.annotations.Language
import java.time.LocalDate

interface UtbetalingStore : Store {
    fun hentUtbetalingForVedtak(vedtakId: Long): Utbetaling?
    fun hentUtbetalingerMedStatusBatchDato(status: UtbetalingStatus = UtbetalingStatus.NY, batchDato: LocalDate): List<Utbetaling>
    fun lagreUtbetaling(utbetaling: Utbetaling): Utbetaling
    fun oppdaterStatus(utbetaling: Utbetaling): Utbetaling
    fun hentUtbetalingerMedBatchId(batchId: String): List<Utbetaling>
}

internal class UtbetalingStorePostgres(sessionFactory: () -> Session) : UtbetalingStore,
    TransactionalStore(sessionFactory) {
    private val allColums = "id, vedtak_id, referanse, utbetalingsdato, opprettet, oppdatert, vedtak, status, batch_dato, batch_id"

    override fun hentUtbetalingForVedtak(vedtakId: Long): Utbetaling? = session {
        @Language("PostgreSQL")
        val sql = """
            SELECT $allColums
            FROM utbetaling_v1
            WHERE vedtak_id = :vedtak_id
        """.trimIndent()
        it.query(sql, mapOf("vedtak_id" to vedtakId)) { row -> mapUtbetaling(row) }
    }

    override fun hentUtbetalingerMedStatusBatchDato(status: UtbetalingStatus, batchDato: LocalDate): List<Utbetaling> = session {
        @Language("PostgreSQL")
        val sql = """
            SELECT $allColums
            FROM utbetaling_v1
            WHERE status = :status and batch_dato <= :batchDato
        """.trimIndent()
        it.queryList(sql, mapOf("status" to status.name, "batchDato" to batchDato)) {
                row ->
            mapUtbetaling(row)
        }
    }

    private fun mapUtbetaling(row: Row) = Utbetaling(
        id = row.long("id"),
        vedtakId = row.long("vedtak_id"),
        referanse = row.string("referanse"),
        utbetalingsdato = row.localDate("utbetalingsdato"),
        opprettet = row.localDateTime("opprettet"),
        oppdatert = row.localDateTime("oppdatert"),
        vedtak = row.json("vedtak"),
        status = UtbetalingStatus.valueOf(row.string("status")),
        batchDato = row.localDate("batch_dato"),
        batchId = row.string("batch_id")
    )

    override fun lagreUtbetaling(utbetaling: Utbetaling): Utbetaling = session {
        @Language("PostgreSQL")
        val sql = """
            INSERT INTO utbetaling_v1 (
                vedtak_id,
                referanse,
                utbetalingsdato,
                opprettet,
                oppdatert,
                vedtak,
                status,
                batch_dato,
                batch_id
            )
            VALUES (
                :vedtak_id,
                :referanse,
                :utbetalingsdato,
                :opprettet,
                :oppdatert,
                :vedtak,
                :status,
                :batch_dato,
                :batch_id
            )
            RETURNING id
        """.trimIndent()
        val id = it.query(
            sql,
            mapOf(
                "vedtak_id" to utbetaling.vedtakId,
                "referanse" to utbetaling.referanse,
                "utbetalingsdato" to utbetaling.utbetalingsdato,
                "opprettet" to utbetaling.opprettet,
                "oppdatert" to utbetaling.oppdatert,
                "vedtak" to pgObjectOf(utbetaling.vedtak),
                "status" to utbetaling.status.name,
                "batch_dato" to utbetaling.batchDato,
                "batch_id" to utbetaling.batchId
            )
        ) { row ->
            row.long("id")
        }
        requireNotNull(id) { "Lagring av utbetaling feilet, id var null" }
        utbetaling.copy(id = id)
    }

    override fun oppdaterStatus(utbetaling: Utbetaling): Utbetaling = session {
        @Language("PostgreSQL")
        val sql = """
            UPDATE utbetaling_v1
            SET status = :status, oppdatert = :oppdatert
            WHERE id = :id
        """.trimIndent()
        it.update(
            sql,
            mapOf(
                "status" to utbetaling.status.name,
                "oppdatert" to utbetaling.oppdatert,
                "id" to utbetaling.id
            )
        ).validate()
        utbetaling
    }

    override fun hentUtbetalingerMedBatchId(batchId: String): List<Utbetaling> = session {
        @Language("PostgreSQL")
        val sql = """
            SELECT $allColums
            FROM utbetaling_v1
            WHERE batch_id = :batchId
        """.trimIndent()
        it.queryList(sql, mapOf("batchId" to batchId)) {
                row ->
            mapUtbetaling(row)
        }
    }
}
