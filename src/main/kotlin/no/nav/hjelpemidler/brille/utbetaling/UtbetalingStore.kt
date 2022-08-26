package no.nav.hjelpemidler.brille.utbetaling

import kotliquery.Row
import no.nav.hjelpemidler.brille.json
import no.nav.hjelpemidler.brille.pgObjectOf
import no.nav.hjelpemidler.brille.store.Store
import no.nav.hjelpemidler.brille.store.query
import no.nav.hjelpemidler.brille.store.queryList
import no.nav.hjelpemidler.brille.store.update
import org.intellij.lang.annotations.Language
import javax.sql.DataSource

interface UtbetalingStore : Store {
    fun hentUtbetalingForVedtak(vedtakId: Long): Utbetaling?
    fun hentUtbetalingerMedStatus(status: UtbetalingStatus = UtbetalingStatus.NY, limit: Int = 100): List<Utbetaling>
    fun lagreUtbetaling(utbetaling: Utbetaling): Utbetaling
    fun oppdaterStatus(utbetaling: Utbetaling): Utbetaling
}

internal class UtbetalingStorePostgres(private val ds: DataSource) : UtbetalingStore {
    private val allColums = "id, vedtak_id, referanse, utbetalingsdato, opprettet, oppdatert, vedtak, status"
    override fun hentUtbetalingForVedtak(vedtakId: Long): Utbetaling? {
        @Language("PostgreSQL")
        val sql = """
            SELECT $allColums
            FROM utbetaling_v1
            WHERE vedtak_id = :vedtak_id
        """.trimIndent()
        return ds.query(sql, mapOf("vedtak_id" to vedtakId)) { mapUtbetaling(it) }
    }

    override fun hentUtbetalingerMedStatus(status: UtbetalingStatus, limit: Int): List<Utbetaling> {
        @Language("PostgreSQL")
        val sql = """
            SELECT $allColums
            FROM utbetaling_v1
            WHERE status = :status LIMIT :limit 
        """.trimIndent()
        return ds.queryList(sql, mapOf("status" to status.name, "limit" to limit)) {
            mapUtbetaling(it)
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
        status = UtbetalingStatus.valueOf(row.string("status"))
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
                status
            )
            VALUES (
                :vedtak_id,
                :referanse,
                :utbetalingsdato,
                :opprettet,
                :oppdatert,
                :vedtak,
                :status
            )
            RETURNING id
        """.trimIndent()
        val id = ds.query(
            sql,
            mapOf(
                "vedtak_id" to utbetaling.vedtakId,
                "referanse" to utbetaling.referanse,
                "utbetalingsdato" to utbetaling.utbetalingsdato,
                "opprettet" to utbetaling.opprettet,
                "oppdatert" to utbetaling.oppdatert,
                "vedtak" to pgObjectOf(utbetaling.vedtak),
                "status" to utbetaling.status.name
            )
        ) { row ->
            row.long("id")
        }
        requireNotNull(id) { "Lagring av utbetaling feilet, id var null" }
        return utbetaling.copy(id = id)
    }

    override fun oppdaterStatus(utbetaling: Utbetaling): Utbetaling {
        @Language("PostgreSQL")
        val sql = """
            UPDATE utbetaling_v1
            SET status = :status, oppdatert = :oppdatert
            WHERE id = :id
        """.trimIndent()
        ds.update(
            sql,
            mapOf(
                "status" to utbetaling.status.name,
                "oppdatert" to utbetaling.oppdatert,
                "id" to utbetaling.id
            )
        ).validate()
        return utbetaling
    }
}
