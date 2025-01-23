package no.nav.hjelpemidler.brille.joarkref

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helse.rapids_rivers.asLocalDateTime
import no.nav.hjelpemidler.brille.utbetaling.PacketListenerWithOnError
import java.util.UUID

private val log = KotlinLogging.logger {}

class JoarkrefRiver(
    rapidsConnection: RapidsConnection,
    private val joarkrefService: JoarkrefService,
) : PacketListenerWithOnError {

    private val eventName = "hm-opprettetOgFerdigstiltBarnebrillerJournalpost"

    init {
        log.info { "registering ${this.javaClass.simpleName}" }
        River(rapidsConnection).apply {
            validate {
                it.demandValue("eventName", eventName)
            }
            validate {
                it.requireKey(
                    "eventId",
                    "sakId",
                    "joarkRef",
                    "dokumentIder",
                    "opprettet",
                )
            }
        }.register(this)
    }

    private val JsonMessage.eventId get() = this["eventId"].textValue().let { UUID.fromString(it) }!!
    private val JsonMessage.sakId get() = this["sakId"].textValue()!!
    private val JsonMessage.joarkRef get() = this["joarkRef"].textValue()!!
    private val JsonMessage.dokumentIder
        get() = this["dokumentIder"].elements().let {
            val ider = mutableListOf<String>()
            while (it.hasNext()) {
                ider.add(it.next().textValue())
            }
            ider
        }
    private val JsonMessage.opprettet get() = this["opprettet"].asLocalDateTime()

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        log.info { "Kvittering for oppretting av journalpost mottatt: eventId=${packet.eventId}, sakId=${packet.sakId}, joarkRef=${packet.joarkRef}, dokumentIder=${packet.dokumentIder}, opprettet=${packet.opprettet}" }
        runBlocking {
            withContext(Dispatchers.IO) {
                joarkrefService.lagreJoarkRef(packet.sakId.toLong(), packet.joarkRef.toLong(), packet.dokumentIder)
            }
        }
    }
}
