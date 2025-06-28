package no.nav.hjelpemidler.brille.utbetaling

import no.nav.hjelpemidler.brille.vedtak.VedtakDto
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

data class Utbetaling(
    val id: Long = -1,
    val vedtakId: Long,
    val vedtak: VedtakDto,
    val referanse: String,
    val utbetalingsdato: LocalDate?,
    val opprettet: LocalDateTime = LocalDateTime.now(),
    val oppdatert: LocalDateTime = LocalDateTime.now(),
    val status: UtbetalingStatus = UtbetalingStatus.NY,
    val batchDato: LocalDate = vedtak.opprettet.toLocalDate(),
    val batchId: String = "${vedtak.orgnr}-${batchDato.format(batchIdDateFormatter)}",
) {
    fun toUtbetalingslinje(): Utbetalingslinje = Utbetalingslinje(
        delytelseId = vedtakId,
        endringskode = "NY",
        klassekode = "BARNEBRILLER",
        fom = LocalDate.now(),
        tom = LocalDate.now(),
        sats = vedtak.beløp,
        satstype = "ENG",
        saksbehandler = "BB",
    )
}

val batchIdDateFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")

enum class UtbetalingStatus {
    NY, // Ved innvilget vedtak, blir det registrert ny utbetaling for vedtaket.
    TIL_UTBETALING, // sendt til utbetaling
    UTBETALT, // utbetalt kvittering fra Utbetalingsmodulen,
    REKJOR, // Manuell verdi for utbetalinger som skal rekjøres
}
