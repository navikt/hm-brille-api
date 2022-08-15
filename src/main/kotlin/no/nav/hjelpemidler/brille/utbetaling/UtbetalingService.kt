package no.nav.hjelpemidler.brille.utbetaling

import no.nav.hjelpemidler.brille.Configuration
import no.nav.hjelpemidler.brille.vedtak.Behandlingsresultat
import no.nav.hjelpemidler.brille.vedtak.Vedtak
import no.nav.hjelpemidler.brille.vedtak.toDto
import org.slf4j.LoggerFactory
import java.time.LocalDateTime

class UtbetalingService(private val store: UtbetalingStore, private val props: Configuration.UtbetalingProperties) {

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
        return "true" == props.enabledUtbetaling
    }

    fun sendTilUtbetaling(utbetaling: Utbetaling): Utbetaling {
        if (utbetaling.id < 0) throw UtbetalingsException("Utbetaling må være registret i databasen")
        if (utbetaling.status != UtbetalingStatus.NY) throw UtbetalingsException("Utbetalingstatus må være NY")
        // TODO legger det ut i kafka
        return store.oppdaterStatus(utbetaling.copy(status = UtbetalingStatus.TIL_UTBETALING, oppdatert = LocalDateTime.now()))
    }

    fun settTilUtbetalt(utbetaling: Utbetaling): Utbetaling {
        if (utbetaling.status != UtbetalingStatus.TIL_UTBETALING) throw UtbetalingsException("Utbetalingstatus må være sendt til Utbetaling")
        return store.oppdaterStatus(utbetaling.copy(status = UtbetalingStatus.UTBETALT, oppdatert = LocalDateTime.now()))
    }
}
