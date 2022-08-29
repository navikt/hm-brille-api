package no.nav.hjelpemidler.brille.utbetaling

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageProblems
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
    val utbetalingsdato: LocalDate,
    val utbetalingslinjer: List<UtbetalingsLinje>

) {
    internal fun toJson(): String {
        return JsonMessage("{}", MessageProblems("")).also {
            it["eventName"] = this.eventName
            it["eventId"] = UUID.randomUUID()
            it["opprettetDato"] = LocalDateTime.now()
            it["orgNr"] = this.orgNr
            it["batchId"] = this.batchId
            it["Utbetaling"] = Utbetaling(
                listOf("BARNBRIL"),
                listOf("NY"),
                "BB",
                orgNr,
                utbetalingslinjer
            )
        }.toJson()
    }

    data class Utbetaling(
        val fagområde: List<String>,
        val endringskode: List<String>,
        val saksbehandler: String,
        val mottaker: String,
        val linjer: List<UtbetalingsLinje>
    )
}

fun Utbetaling.toUtbetalingsLinje(): UtbetalingsLinje = UtbetalingsLinje(
    delytelseId = id,
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
    utbetalingsdato = utbetalinger.first().utbetalingsdato,
    utbetalingslinjer = utbetalinger.map { it.toUtbetalingsLinje() }
)

fun List<Utbetaling>.toUtbetalingsBatchList(): List<UtbetalingsBatch> =
    groupBy { it.batchId }.map { UtbetalingsBatch(utbetalinger = it.value) }
