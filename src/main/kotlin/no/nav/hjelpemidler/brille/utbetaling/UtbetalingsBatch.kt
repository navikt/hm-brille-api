package no.nav.hjelpemidler.brille.utbetaling

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

data class UtbetalingsBatch(
    val utbetalinger: List<Utbetaling>,
    val orgNr: String = utbetalinger[0].vedtak.orgnr,
    val batchId: String = utbetalinger[0].batchId
) {
    init {
        check(utbetalinger.all { it.vedtak.orgnr == orgNr })
        check(utbetalinger.all { it.batchId == batchId })
    }
}

data class UtbetalingsLinje(
    val delytelseId: Long,
    val endringskode: String,
    val klassekode: String,
    val fom: LocalDate,
    val tom: LocalDate,
    val sats: Int,
    val satstype: String,
    val saksbehandler: String
)

data class UtbetalingsMelding(
    val eventName: String = "hm-barnebrille-utbetaling",
    val type: String = "Batch",
    val opprettetDato: LocalDateTime,
    val orgNr: String,
    val batchId: String,
    val utbetalingslinjer: List<UtbetalingsLinje>

) {
    internal fun toJson() = mapOf(
        "eventName" to this.eventName,
        "eventId" to UUID.randomUUID(),
        "opprettetDato" to LocalDateTime.now(),
        "orgNr" to this.orgNr,
        "batchId" to this.batchId,
        "Utbetaling" to Utbetaling(
            fagområde = "BARNBRIL",
            endringskode = "NY",
            saksbehandler = "BB",
            mottaker = orgNr,
            linjer = utbetalingslinjer
        )

    )

    data class Utbetaling(
        val fagområde: String,
        val endringskode: String,
        val saksbehandler: String,
        val mottaker: String,
        val linjer: List<UtbetalingsLinje>
    )
}

fun Utbetaling.toUtbetalingsLinje(): UtbetalingsLinje = UtbetalingsLinje(
    delytelseId = vedtakId,
    endringskode = "NY",
    klassekode = "BARNEBRILLER",
    fom = LocalDate.now(),
    tom = LocalDate.now(),
    sats = vedtak.beløp.toInt(),
    satstype = "ENG",
    saksbehandler = "BB"
)

fun UtbetalingsBatch.lagMelding(): UtbetalingsMelding = UtbetalingsMelding(
    opprettetDato = LocalDateTime.now(),
    orgNr = orgNr,
    batchId = batchId,
    utbetalingslinjer = utbetalinger.map { it.toUtbetalingsLinje() }
)

fun List<Utbetaling>.toUtbetalingsBatchList(): List<UtbetalingsBatch> =
    groupBy { it.batchId }.map { UtbetalingsBatch(utbetalinger = it.value) }
