package no.nav.hjelpemidler.brille.kafka

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.KafkaRapid
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.MessageProblems
import no.nav.helse.rapids_rivers.River
import org.slf4j.LoggerFactory

class VedtakOpprettetRiver(rapid: KafkaRapid) : River.PacketListener {

    private val river: River

    private val eventName = "hm-barnebrillevedtak-opprettet"

    companion object {
        private val LOG = LoggerFactory.getLogger(VedtakOpprettetRiver::class.java)
    }

    init {
        LOG.info("registering ${this.javaClass.simpleName}")
        river = River(rapid).apply {
            validate { it.demandValue("@event_name", eventName) }
        }.register(this)
    }

    override fun onError(problems: MessageProblems, context: MessageContext) {
        LOG.error("Consumer got problems $problems")
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        LOG.info("Tester river fått sakId: ${packet["sakId"].asText()}")
    }
}
