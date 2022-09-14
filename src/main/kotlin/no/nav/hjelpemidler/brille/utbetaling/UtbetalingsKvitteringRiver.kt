package no.nav.hjelpemidler.brille.utbetaling

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.hjelpemidler.brille.internal.MetricsConfig
import org.slf4j.LoggerFactory

class UtbetalingsKvitteringRiver(
    rapidsConnection: RapidsConnection,
    val utbetalingService: UtbetalingService,
    private val metricsConfig: MetricsConfig,
) : PacketListenerWithOnError {

    private val eventName = "hm-oppdragHarUtbetaltKrav"

    companion object {
        private val LOG = LoggerFactory.getLogger(UtbetalingsKvitteringRiver::class.java)
    }

    init {
        LOG.info("registering ${this.javaClass.simpleName}")
        River(rapidsConnection).apply {
            validate {
                it.demandValue("eventName", eventName)
            }
            validate {
                it.requireKey(
                    "eventId",
                    "opprettet",
                    "tssId",
                    "orgNr",
                    "avstemmingsnøkkel",
                    "status",
                    "feilkode_oppdrag",
                    "beskrivelse",
                    "originalXml",
                    "batchId"
                )
            }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        LOG.info("Mottok kvitteringsevent")
        runBlocking {
            withContext(Dispatchers.IO) {
                launch {
                    val batchId = packet["batchId"].asText()
                    val status = packet["status"].asText()

                    LOG.info("Mottok kvittering med status $status hm-utbetaling for batchId: $batchId")
                    val utbetalingerTilOppdatering = utbetalingService.hentUtbetalingerMedBatchId(batchId)
                    utbetalingerTilOppdatering.forEach {
                        if (it.status == UtbetalingStatus.TIL_UTBETALING) {
                            utbetalingService.settTilUtbetalt(it)
                        }
                    }
                    LOG.info("Oppdaterte alle rader. for batchId $batchId")
                    metricsConfig.registry
                        .counter("utbetaling_kvittering_mottat", "status", status)
                        .increment()
                }
            }
        }
    }
}
