package no.nav.hjelpemidler.brille.utbetaling

import no.nav.hjelpemidler.brille.db.DatabaseContext
import no.nav.hjelpemidler.brille.db.transaction
import no.nav.hjelpemidler.brille.kafka.KafkaService
import no.nav.hjelpemidler.brille.vedtak.Behandlingsresultat
import no.nav.hjelpemidler.brille.vedtak.Vedtak
import no.nav.hjelpemidler.brille.vedtak.toDto
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.time.LocalDateTime

class UtbetalingService(
    private val databaseContext: DatabaseContext,
    private val kafkaService: KafkaService
) {

    companion object {
        private val LOG = LoggerFactory.getLogger(UtbetalingService::class.java)
    }

    suspend fun <T> opprettNyUtbetaling(vedtak: Vedtak<T>): Utbetaling {
        if (vedtak.behandlingsresultat != Behandlingsresultat.INNVILGET)
            throw UtbetalingsException("Vedtaket må være innvilget")
        return transaction(databaseContext) { ctx ->
            ctx.vedtakStore.fjernFraVedTakKø(vedtak.id)
            ctx.utbetalingStore.lagreUtbetaling(
                Utbetaling(
                    vedtakId = vedtak.id,
                    referanse = vedtak.bestillingsreferanse,
                    utbetalingsdato = null,
                    vedtak = vedtak.toDto()
                )
            )
        }
    }

    suspend fun sendBatchTilUtbetaling(utbetalingsBatchDTO: UtbetalingsBatchDTO, tssIdent: String) {
        transaction(databaseContext) { ctx ->
            ctx.utbetalingStore.lagreUtbetalingsBatch(utbetalingsBatchDTO.toUtbetalingsBatch())
            utbetalingsBatchDTO.utbetalinger.forEach {
                ctx.utbetalingStore.oppdaterStatus(
                    it.copy(
                        status = UtbetalingStatus.TIL_UTBETALING,
                        oppdatert = LocalDateTime.now()
                    )
                )
            }
            kafkaService.produceEvent(null, utbetalingsBatchDTO.lagMelding(tssIdent).toJson())
        }
    }

    suspend fun settTilUtbetalt(utbetaling: Utbetaling): Utbetaling {
        if (utbetaling.status != UtbetalingStatus.TIL_UTBETALING) throw UtbetalingsException("Utbetalingstatus må være sendt til Utbetaling")
        return transaction(databaseContext) { ctx ->
            ctx.utbetalingStore.oppdaterStatusOgUtbetalingsdato(
                utbetaling.copy(
                    status = UtbetalingStatus.UTBETALT,
                    oppdatert = LocalDateTime.now(),
                    utbetalingsdato = LocalDate.now()
                )
            )
        }
    }

    suspend fun hentUtbetalingerMedStatusBatchDato(batchDato: LocalDate): List<Utbetaling> {
        return transaction(databaseContext) { ctx ->
            ctx.utbetalingStore.hentUtbetalingerMedStatusBatchDato(status = UtbetalingStatus.NY, batchDato = batchDato)
        }
    }

    suspend fun hentUtbetalingerMedBatchId(batchId: String): List<Utbetaling> {
        return transaction(databaseContext) { ctx ->
            ctx.utbetalingStore.hentUtbetalingerMedBatchId(batchId)
        }
    }

    suspend fun hentUtbetalingForVedtak(vedtakId: Long): Utbetaling? {
        return transaction(databaseContext) {
                ctx ->
            ctx.utbetalingStore.hentUtbetalingForVedtak(vedtakId)
        }
    }
}
