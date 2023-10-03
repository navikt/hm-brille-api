package no.nav.hjelpemidler.brille.joarkref

import com.fasterxml.jackson.module.kotlin.readValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helse.rapids_rivers.asLocalDateTime
import no.nav.hjelpemidler.brille.jsonMapper
import no.nav.hjelpemidler.brille.utbetaling.PacketListenerWithOnError
import org.slf4j.LoggerFactory
import java.util.UUID

class JoarkrefDokumentIderRiver(
    rapidsConnection: RapidsConnection,
    private val joarkrefService: JoarkrefService,
) : PacketListenerWithOnError {

    private val eventName = "temp-joarkref-dokumentider-svar"

    companion object {
        private val LOG = LoggerFactory.getLogger(JoarkrefDokumentIderRiver::class.java)
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
                    "resultater",
                )
            }
        }.register(this)
    }

    private val JsonMessage.eventId get() = this["eventId"].textValue().let { UUID.fromString(it) }!!
    private val JsonMessage.opprettet get() = this["opprettet"].asLocalDateTime()
    private val JsonMessage.resultater get() = this["resultater"].let {
        val value: Map<Long, Array<String>> = jsonMapper.readValue(it.toString())
        value
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        val resultater = packet.resultater
        LOG.info("Batch av dokumentIder mottatt: eventId=${packet.eventId}, opprettet=${packet.opprettet}, antallResultater=${resultater.count()}")
        runBlocking {
            withContext(Dispatchers.IO) {
                resultater.forEach { entry ->
                    val joarkref = entry.key
                    val dokumentIder = entry.value.toList()
                    joarkrefService.oppdaterJoarkrefMedNyeDokumentIder(joarkref, dokumentIder)
                }
            }
        }
    }
}
