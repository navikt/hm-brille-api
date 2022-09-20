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
import java.time.LocalDateTime

interface UtbetalingStore : Store {
    fun hentUtbetalingForVedtak(vedtakId: Long): Utbetaling?
    fun hentUtbetalingerMedStatusBatchDatoOpprettet(status: UtbetalingStatus = UtbetalingStatus.NY, batchDato: LocalDate, opprettet: LocalDateTime? = null): List<Utbetaling>
    fun hentUtbetalingerMedStatus(status: UtbetalingStatus = UtbetalingStatus.REKJOR): List<Utbetaling>
    fun lagreUtbetaling(utbetaling: Utbetaling): Utbetaling
    fun oppdaterStatus(utbetaling: Utbetaling): Utbetaling
    fun hentUtbetalingerMedBatchId(batchId: String): List<Utbetaling>
    fun lagreUtbetalingsBatch(utbetalingsBatch: UtbetalingsBatch): Int?
    fun hentUtbetalingsBatch(batchId: String): UtbetalingsBatch?
    fun oppdaterStatusOgUtbetalingsdato(utbetaling: Utbetaling): Utbetaling
    fun hentAntallUtbetalingerMedStatus(status: UtbetalingStatus): Int
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

    override fun hentUtbetalingerMedStatusBatchDatoOpprettet(status: UtbetalingStatus, batchDato: LocalDate, opprettet: LocalDateTime?): List<Utbetaling> = session {
        @Language("PostgreSQL")
        var sql = """
            SELECT $allColums
            FROM utbetaling_v1
            WHERE status = :status AND batch_dato <= :batchDato
        """.trimIndent()

        opprettet?.let {
            sql = sql.plus(" AND opprettet <= :opprettet")
        }

        it.queryList(sql, mapOf("status" to status.name, "batchDato" to batchDato, "opprettet" to opprettet)) {
                row ->
            mapUtbetaling(row)
        }
    }

    override fun hentUtbetalingerMedStatus(status: UtbetalingStatus): List<Utbetaling> = session {
        @Language("PostgreSQL")
        val sql = """
            SELECT $allColums
            FROM utbetaling_v1
            WHERE status = :status
        """.trimIndent()
        it.queryList(sql, mapOf("status" to status.name)) {
                row ->
            mapUtbetaling(row)
        }
    }

    private fun mapUtbetaling(row: Row) = Utbetaling(
        id = row.long("id"),
        vedtakId = row.long("vedtak_id"),
        referanse = row.string("referanse"),
        utbetalingsdato = row.localDateOrNull("utbetalingsdato"),
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

    override fun oppdaterStatusOgUtbetalingsdato(utbetaling: Utbetaling): Utbetaling = session {
        @Language("PostgreSQL")
        val sql = """
            UPDATE utbetaling_v1
            SET status = :status, oppdatert = :oppdatert, utbetalingsdato = :utbetalingsdato
            WHERE id = :id
        """.trimIndent()
        it.update(
            sql,
            mapOf(
                "status" to utbetaling.status.name,
                "oppdatert" to utbetaling.oppdatert,
                "utbetalingsdato" to utbetaling.utbetalingsdato,
                "id" to utbetaling.id
            )
        ).validate()
        utbetaling
    }

    override fun hentAntallUtbetalingerMedStatus(status: UtbetalingStatus): Int = session {
        @Language("PostgreSQL")
        val sql = """
            SELECT COUNT(*) as total FROM utbetaling_v1 where status = :status
        """.trimIndent()
        it.query(sql, mapOf("status" to status.name)) { row -> row.int("total") }!!
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

    override fun lagreUtbetalingsBatch(utbetalingsBatch: UtbetalingsBatch): Int? = session {
        @Language("PostgreSQL")
        val sql = """
            INSERT INTO utbetalingsbatch_v1 (
                batch_id,
                antall_utbetalinger,
                totalbelop,
                opprettet
             )
            VALUES (
                :batch_id,
                :antall_utbetalinger,
                :totalbelop,
                :opprettet
            )
        """.trimIndent()
        it.update(
            sql,
            mapOf(
                "batch_id" to utbetalingsBatch.batchId,
                "antall_utbetalinger" to utbetalingsBatch.antallUtbetalinger,
                "totalbelop" to utbetalingsBatch.totalbeløp,
                "opprettet" to utbetalingsBatch.opprettet,
            )
        ).rowCount
    }

    override fun hentUtbetalingsBatch(batchId: String): UtbetalingsBatch? = session {
        @Language("PostgreSQL")
        val sql = """
            SELECT batch_id, antall_utbetalinger, totalbelop,opprettet
            FROM utbetalingsbatch_v1
            WHERE batch_id = :batchId
        """.trimIndent()
        it.query(sql, mapOf("batchId" to batchId)) {
                row ->
            UtbetalingsBatch(
                batchId = row.string("batch_id"),
                totalbeløp = row.bigDecimal("totalbelop"),
                antallUtbetalinger = row.int("antall_utbetalinger"),
                opprettet = row.localDateTime("opprettet")
            )
        }
    }
}
