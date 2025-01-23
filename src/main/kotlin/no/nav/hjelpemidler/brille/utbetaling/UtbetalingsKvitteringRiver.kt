package no.nav.hjelpemidler.brille.utbetaling

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.hjelpemidler.brille.internal.MetricsConfig

private val log = KotlinLogging.logger {}

class UtbetalingsKvitteringRiver(
    rapidsConnection: RapidsConnection,
    val utbetalingService: UtbetalingService,
    private val metricsConfig: MetricsConfig,
) : PacketListenerWithOnError {
    private val eventName = "hm-oppdragHarUtbetaltKrav"

    init {
        log.info { "registering ${this.javaClass.simpleName}" }
        River(rapidsConnection).apply {
            validate {
                it.demandValue("eventName", eventName)
            }
            validate {
                it.requireKey(
                    "eventId",
                    "opprettet",
                    "tssId",
                    "avstemmingsnøkkel",
                    "status",
                    "feilkode_oppdrag",
                    "beskrivelse",
                    "originalXml",
                    "batchId",
                )
            }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        log.info { "Mottok kvitteringsevent" }
        runBlocking {
            withContext(Dispatchers.IO) {
                launch {
                    val batchId = packet["batchId"].asText()
                    val status = packet["status"].asText()

                    log.info { "Mottok kvittering med status $status hm-utbetaling for batchId: $batchId" }
                    val utbetalingerTilOppdatering = utbetalingService.hentUtbetalingerMedBatchId(batchId)
                    utbetalingerTilOppdatering.forEach {
                        if (it.status == UtbetalingStatus.TIL_UTBETALING) {
                            utbetalingService.settTilUtbetalt(it)
                        }
                    }
                    log.info { "Oppdaterte alle rader. for batchId $batchId" }
                    metricsConfig.registry
                        .counter("utbetaling_kvittering_mottat", "status", status)
                        .increment()
                }
            }
        }
    }
}
