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
    val utbetalingsdato: LocalDate,
    val opprettet: LocalDateTime = LocalDateTime.now(),
    val oppdatert: LocalDateTime = LocalDateTime.now(),
    val status: UtbetalingStatus = UtbetalingStatus.NY,
    val batchId: String = vedtak.orgnr + vedtak.opprettet.toLocalDate().format(batchIdDateFormatter)
)

val batchIdDateFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")

enum class UtbetalingStatus {
    NY, // Ved innvilget vedtak, blir det registrert ny utbetaling for vedtaket.
    TIL_UTBETALING, // sendt til utbetaling
    UTBETALT // utbetalt kvittering fra Utbetalingsmodulen
}
