package no.nav.hjelpemidler.brille.utbetaling

import java.math.BigDecimal
import java.time.LocalDate


data class UtbetalingsBatch(val utbetalinger: List<Utbetaling>,
                            val orgNr: String,
                            val batchId: String = "01" + utbetalinger.first().vedtakId)
{
    init {
        check(utbetalinger.size<100)
        check(utbetalinger.all { it.vedtak.orgnr == orgNr })
    }
}

data class UtbetalingsLinje(val id: Long, val vedtakId:Long, val referanse: String, val belop: BigDecimal)

data class UtbetalingsMelding(val eventName: String = "hm-utbetaling-melding", val type: String ="Batch",
                              val mottaker: String, val batchId: String, val utbetalingsdato: LocalDate,
                              val utbetalingslinjer: List<UtbetalingsLinje>)


fun Utbetaling.toUtbetalingsLinje(): UtbetalingsLinje = UtbetalingsLinje(id = id, vedtakId = vedtakId,
referanse = vedtak.bestillingsreferanse, belop = vedtak.beløp)

fun UtbetalingsBatch.lagMelding(): UtbetalingsMelding = UtbetalingsMelding(
    mottaker = orgNr, batchId = batchId, utbetalingsdato = utbetalinger.first().utbetalingsdato,
    utbetalingslinjer = utbetalinger.map { it.toUtbetalingsLinje()}
)
