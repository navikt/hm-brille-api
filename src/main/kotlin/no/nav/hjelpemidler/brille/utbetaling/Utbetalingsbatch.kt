package no.nav.hjelpemidler.brille.utbetaling

import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

data class Utbetalingsbatch(
    val batchId: String,
    val totalbeløp: BigDecimal,
    val antallUtbetalinger: Int,
    val opprettet: LocalDateTime = LocalDateTime.now(),
)

data class UtbetalingsbatchDTO(
    val utbetalinger: List<Utbetaling>,
    val orgNr: String = utbetalinger[0].vedtak.orgnr,
    val batchId: String = utbetalinger[0].batchId,
) {
    init {
        check(utbetalinger.all { it.vedtak.orgnr == orgNr })
        check(utbetalinger.all { it.batchId == batchId })
    }

    fun toUtbetalingsbatch(): Utbetalingsbatch = Utbetalingsbatch(
        batchId = batchId,
        antallUtbetalinger = utbetalinger.size,
        totalbeløp = utbetalinger.sumOf { it.vedtak.beløp },
    )

    fun lagUtbetalingsmelding(tssIdent: String): Utbetalingsmelding = Utbetalingsmelding(
        opprettetDato = LocalDateTime.now(),
        orgNr = orgNr,
        tssIdent = tssIdent,
        batchId = batchId,
        utbetalingslinjer = utbetalinger.map { it.toUtbetalingslinje() },
    )
}

data class Utbetalingslinje(
    val delytelseId: Long,
    val endringskode: String,
    val klassekode: String,
    val fom: LocalDate,
    val tom: LocalDate,
    val sats: Int,
    val satstype: String,
    val saksbehandler: String,
)

data class Utbetalingsmelding(
    val eventName: String = "hm-barnebrille-utbetaling",
    val type: String = "Batch",
    val opprettetDato: LocalDateTime,
    val orgNr: String,
    val tssIdent: String,
    val batchId: String,
    val utbetalingslinjer: List<Utbetalingslinje>,

) {
    fun toJson() = mapOf(
        "eventName" to this.eventName,
        "eventId" to UUID.randomUUID(),
        "opprettetDato" to LocalDateTime.now(),
        "orgNr" to this.orgNr,
        "tssIdent" to this.tssIdent,
        "batchId" to this.batchId,
        "Utbetaling" to Utbetaling(
            fagområde = "BARNBRIL",
            endringskode = "NY",
            saksbehandler = "BB",
            mottaker = orgNr,
            linjer = utbetalingslinjer,
        ),
    )

    data class Utbetaling(
        val fagområde: String,
        val endringskode: String,
        val saksbehandler: String,
        val mottaker: String,
        val linjer: List<Utbetalingslinje>,
    )
}

fun List<Utbetaling>.toUtbetalingBatchList(): List<UtbetalingsbatchDTO> =
    groupBy { it.batchId }.map { UtbetalingsbatchDTO(utbetalinger = it.value) }
