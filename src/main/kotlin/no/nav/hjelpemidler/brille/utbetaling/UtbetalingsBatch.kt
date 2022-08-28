package no.nav.hjelpemidler.brille.utbetaling

import java.math.BigDecimal
import java.time.LocalDate

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

data class UtbetalingsLinje(val id: Long, val vedtakId: Long, val referanse: String, val belop: BigDecimal)

data class UtbetalingsMelding(
    val eventName: String = "hm-utbetaling-melding",
    val type: String = "Batch",
    val mottaker: String,
    val batchId: String,
    val utbetalingsdato: LocalDate,
    val utbetalingslinjer: List<UtbetalingsLinje>
)

fun Utbetaling.toUtbetalingsLinje(): UtbetalingsLinje = UtbetalingsLinje(
    id = id, vedtakId = vedtakId,
    referanse = vedtak.bestillingsreferanse, belop = vedtak.beløp
)

fun UtbetalingsBatch.lagMelding(): UtbetalingsMelding = UtbetalingsMelding(
    mottaker = orgNr, batchId = batchId, utbetalingsdato = utbetalinger.first().utbetalingsdato,
    utbetalingslinjer = utbetalinger.map { it.toUtbetalingsLinje() }
)

fun List<Utbetaling>.toUtbetalingsBatchList(): List<UtbetalingsBatch> =
    groupBy { it.batchId }.map { UtbetalingsBatch(utbetalinger = it.value) }
