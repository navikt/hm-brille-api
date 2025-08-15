package no.nav.hjelpemidler.brille.utbetaling

import no.nav.hjelpemidler.database.JdbcOperations
import no.nav.hjelpemidler.database.Row
import no.nav.hjelpemidler.database.Store
import no.nav.hjelpemidler.database.pgJsonbOf
import org.intellij.lang.annotations.Language
import java.time.LocalDate
import java.time.LocalDateTime

interface UtbetalingStore : Store {
    fun hentUtbetalingForVedtak(vedtakId: Long): Utbetaling?
    fun hentUtbetalingerMedStatusBatchDatoOpprettet(
        status: UtbetalingStatus = UtbetalingStatus.NY,
        batchDato: LocalDate,
        opprettet: LocalDateTime? = null,
    ): List<Utbetaling>

    fun hentUtbetalingerMedStatus(status: UtbetalingStatus = UtbetalingStatus.REKJOR): List<Utbetaling>
    fun lagreUtbetaling(utbetaling: Utbetaling): Utbetaling
    fun oppdaterStatus(utbetaling: Utbetaling): Utbetaling
    fun hentUtbetalingerMedBatchId(batchId: String): List<Utbetaling>
    fun lagreUtbetalingsbatch(utbetalingsbatch: Utbetalingsbatch): Int?
    fun hentUtbetalingsbatch(batchId: String): Utbetalingsbatch?
    fun oppdaterStatusOgUtbetalingsdato(utbetaling: Utbetaling): Utbetaling
    fun hentAntallUtbetalingerMedStatus(status: UtbetalingStatus): Int
}

class UtbetalingStorePostgres(private val tx: JdbcOperations) : UtbetalingStore {
    private val allColums =
        "id, vedtak_id, referanse, utbetalingsdato, opprettet, oppdatert, vedtak, status, batch_dato, batch_id"

    override fun hentUtbetalingForVedtak(vedtakId: Long): Utbetaling? {
        @Language("PostgreSQL")
        val sql = """
            SELECT $allColums
            FROM utbetaling_v1
            WHERE vedtak_id = :vedtak_id
        """.trimIndent()
        return tx.singleOrNull(sql, mapOf("vedtak_id" to vedtakId)) { row -> mapUtbetaling(row) }
    }

    override fun hentUtbetalingerMedStatusBatchDatoOpprettet(
        status: UtbetalingStatus,
        batchDato: LocalDate,
        opprettet: LocalDateTime?,
    ): List<Utbetaling> {
        @Language("PostgreSQL")
        var sql = """
            SELECT $allColums
            FROM utbetaling_v1
            WHERE status = :status AND batch_dato <= :batchDato
        """.trimIndent()

        opprettet?.let {
            sql = sql.plus(" AND opprettet <= :opprettet")
        }

        return tx.list(sql, mapOf("status" to status.name, "batchDato" to batchDato, "opprettet" to opprettet)) { row ->
            mapUtbetaling(row)
        }
    }

    override fun hentUtbetalingerMedStatus(status: UtbetalingStatus): List<Utbetaling> {
        @Language("PostgreSQL")
        val sql = """
            SELECT $allColums
            FROM utbetaling_v1
            WHERE status = :status
        """.trimIndent()
        return tx.list(sql, mapOf("status" to status.name)) { row ->
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
        status = row.enum("status"),
        batchDato = row.localDate("batch_dato"),
        batchId = row.string("batch_id"),
    )

    override fun lagreUtbetaling(utbetaling: Utbetaling): Utbetaling {
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
        val id = tx.single(
            sql,
            mapOf(
                "vedtak_id" to utbetaling.vedtakId,
                "referanse" to utbetaling.referanse,
                "utbetalingsdato" to utbetaling.utbetalingsdato,
                "opprettet" to utbetaling.opprettet,
                "oppdatert" to utbetaling.oppdatert,
                "vedtak" to pgJsonbOf(utbetaling.vedtak),
                "status" to utbetaling.status.name,
                "batch_dato" to utbetaling.batchDato,
                "batch_id" to utbetaling.batchId,
            ),
        ) { row ->
            row.long("id")
        }
        return utbetaling.copy(id = id)
    }

    override fun oppdaterStatus(utbetaling: Utbetaling): Utbetaling {
        @Language("PostgreSQL")
        val sql = """
            UPDATE utbetaling_v1
            SET status = :status, oppdatert = :oppdatert
            WHERE id = :id
        """.trimIndent()
        tx.update(
            sql,
            mapOf(
                "status" to utbetaling.status.name,
                "oppdatert" to utbetaling.oppdatert,
                "id" to utbetaling.id,
            ),
        ).expect(1)
        return utbetaling
    }

    override fun oppdaterStatusOgUtbetalingsdato(utbetaling: Utbetaling): Utbetaling {
        @Language("PostgreSQL")
        val sql = """
            UPDATE utbetaling_v1
            SET status = :status, oppdatert = :oppdatert, utbetalingsdato = :utbetalingsdato
            WHERE id = :id
        """.trimIndent()
        tx.update(
            sql,
            mapOf(
                "status" to utbetaling.status.name,
                "oppdatert" to utbetaling.oppdatert,
                "utbetalingsdato" to utbetaling.utbetalingsdato,
                "id" to utbetaling.id,
            ),
        ).expect(1)
        return utbetaling
    }

    override fun hentAntallUtbetalingerMedStatus(status: UtbetalingStatus): Int {
        @Language("PostgreSQL")
        val sql = """
            SELECT COUNT(*) AS total FROM utbetaling_v1 WHERE status = :status
        """.trimIndent()
        return tx.single(sql, mapOf("status" to status.name)) { row -> row.int("total") }
    }

    override fun hentUtbetalingerMedBatchId(batchId: String): List<Utbetaling> {
        @Language("PostgreSQL")
        val sql = """
            SELECT $allColums
            FROM utbetaling_v1
            WHERE batch_id = :batchId
        """.trimIndent()
        return tx.list(sql, mapOf("batchId" to batchId)) { row ->
            mapUtbetaling(row)
        }
    }

    override fun lagreUtbetalingsbatch(utbetalingsbatch: Utbetalingsbatch): Int {
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
        return tx.update(
            sql,
            mapOf(
                "batch_id" to utbetalingsbatch.batchId,
                "antall_utbetalinger" to utbetalingsbatch.antallUtbetalinger,
                "totalbelop" to utbetalingsbatch.totalbeløp,
                "opprettet" to utbetalingsbatch.opprettet,
            ),
        ).actualRowCount
    }

    override fun hentUtbetalingsbatch(batchId: String): Utbetalingsbatch? {
        @Language("PostgreSQL")
        val sql = """
            SELECT batch_id, antall_utbetalinger, totalbelop,opprettet
            FROM utbetalingsbatch_v1
            WHERE batch_id = :batchId
        """.trimIndent()
        return tx.singleOrNull(sql, mapOf("batchId" to batchId)) { row ->
            Utbetalingsbatch(
                batchId = row.string("batch_id"),
                totalbeløp = row.bigDecimal("totalbelop"),
                antallUtbetalinger = row.int("antall_utbetalinger"),
                opprettet = row.localDateTime("opprettet"),
            )
        }
    }
}
