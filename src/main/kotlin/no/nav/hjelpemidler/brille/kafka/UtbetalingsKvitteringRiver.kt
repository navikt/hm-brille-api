package no.nav.hjelpemidler.brille.kafka

import org.slf4j.LoggerFactory

class UtbetalingsKvitteringRiver(rapid: KafkaRapid) : River.PacketListener {

    private val river: River

    private val eventName = "utbetaling_kvittering"

    companion object {
        private val LOG = LoggerFactory.getLogger(UtbetalingsKvitteringRiver::class.java)
    }

    init {
        LOG.info("registering ${this.javaClass.simpleName}")
        river = River(rapid).apply {
            validate { it.demandValue("@event_name", eventName) }
            // validate { it.requireKey("important_key") }
            // validate { it.requireValue("important_key", "important") }
        }.register(this)
    }

    override fun onError(problems: MessageProblems, context: MessageContext) {
        LOG.error("Consumer got problems $problems")
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        LOG.info(packet["any_variable"].asText())
    }
}
