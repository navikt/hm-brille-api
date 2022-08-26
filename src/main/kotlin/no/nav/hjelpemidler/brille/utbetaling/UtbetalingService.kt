package no.nav.hjelpemidler.brille.utbetaling

import no.nav.hjelpemidler.brille.Configuration
import no.nav.hjelpemidler.brille.kafka.KafkaService
import no.nav.hjelpemidler.brille.vedtak.Behandlingsresultat
import no.nav.hjelpemidler.brille.vedtak.Vedtak
import no.nav.hjelpemidler.brille.vedtak.toDto
import org.slf4j.LoggerFactory
import java.time.LocalDateTime

class UtbetalingService(private val store: UtbetalingStore, private val props: Configuration.UtbetalingProperties,
                        private val kafkaService: KafkaService) {

    companion object {
        private val LOG = LoggerFactory.getLogger(UtbetalingService::class.java)
    }

    init {
        LOG.info("Utbetalingservice er skrudd ${if (isEnabled()) "på" else "av"}")
    }

    fun <T> opprettNyUtbetaling(vedtak: Vedtak<T>): Utbetaling {
        if (vedtak.behandlingsresultat != Behandlingsresultat.INNVILGET)
            throw UtbetalingsException("Vedtaket må være innvilget")
        return store.lagreUtbetaling(
            Utbetaling(
                vedtakId = vedtak.id,
                referanse = vedtak.bestillingsreferanse,
                utbetalingsdato = vedtak.bestillingsdato,
                vedtak = vedtak.toDto()
            )
        )
    }

    fun isEnabled(): Boolean {
        return props.enabledUtbetaling
    }

    fun sendBatchTilUtbetaling(utbetalingsBatch: UtbetalingsBatch) {
        kafkaService.produceEvent(utbetalingsBatch.batchId, utbetalingsBatch.lagMelding())
        utbetalingsBatch.utbetalinger.forEach {
            store.oppdaterStatus(it.copy(status = UtbetalingStatus.TIL_UTBETALING,
                oppdatert = LocalDateTime.now(), batchId = utbetalingsBatch.batchId))
        }
    }

    fun settTilUtbetalt(utbetaling: Utbetaling): Utbetaling {
        if (utbetaling.status != UtbetalingStatus.TIL_UTBETALING) throw UtbetalingsException("Utbetalingstatus må være sendt til Utbetaling")
        return store.oppdaterStatus(utbetaling.copy(status = UtbetalingStatus.UTBETALT, oppdatert = LocalDateTime.now()))
    }

    fun hentUtbetalingerSomSkalTilUtbetaling(): List<Utbetaling> {
        return store.hentUtbetalingerMedStatus(status= UtbetalingStatus.NY, limit=100)
    }

}
