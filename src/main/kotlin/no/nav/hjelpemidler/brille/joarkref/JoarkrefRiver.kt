package no.nav.hjelpemidler.brille.joarkref

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helse.rapids_rivers.asLocalDateTime
import no.nav.hjelpemidler.brille.db.DatabaseContext
import no.nav.hjelpemidler.brille.db.transaction
import no.nav.hjelpemidler.brille.utbetaling.PacketListenerWithOnError
import org.slf4j.LoggerFactory
import java.util.UUID

class JoarkrefRiver(
    rapidsConnection: RapidsConnection,
    private val databaseContext: DatabaseContext
) : PacketListenerWithOnError {

    private val eventName = "hm-opprettetOgFerdigstiltBarnebrillerJournalpost"

    companion object {
        private val LOG = LoggerFactory.getLogger(JoarkrefRiver::class.java)
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
                    "sakId",
                    "joarkRef",
                    "opprettet",
                )
            }
        }.register(this)
    }

    private val JsonMessage.eventId get() = this["eventId"].textValue().let { UUID.fromString(it) }!!
    private val JsonMessage.sakId get() = this["sakId"].textValue()!!
    private val JsonMessage.joarkRef get() = this["joarkRef"].textValue()!!
    private val JsonMessage.opprettet get() = this["opprettet"].asLocalDateTime()

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        LOG.info("Kvittering for oppretting av journalpost mottatt: eventId=${packet.eventId}, sakId=${packet.sakId}, joarkRef=${packet.joarkRef}, opprettet=${packet.opprettet}")
        runBlocking {
            withContext(Dispatchers.IO) {
                transaction(databaseContext) { ctx ->
                    ctx.joarkrefStore.lagreJoarkRef(packet.sakId, packet.joarkRef)
                }
            }
        }
    }
}
