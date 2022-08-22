package no.nav.hjelpemidler.brille.kafka

import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.util.UUID
import kotlin.concurrent.thread

class RapidAppAttached() {

    private val kafkaConfig: KafkaConfig
    private val rapid: KafkaRapid
    private val rapidTopic: String

    companion object {
        private val LOG = LoggerFactory.getLogger(RapidAppAttached::class.java)
    }

    init {
        kafkaConfig = KafkaConfig(bootstrapServers = "localhost:9092", consumerGroupId = "hm-brille-api-v1")
        rapidTopic = "myRapidTopic"
        rapid = KafkaRapid.create(kafkaConfig, rapidTopic, emptyList())
        register()
    }

    private fun register() {
        LOG.info("Register listeners")
        UtbetalingsKvitteringRiver(rapid)
    }

    fun startUp() {
        thread(isDaemon = false) {
            rapid.start()
        }
    }

    fun publishSyncronized(message: String) {
        rapid.publish(
            JsonMessage.newMessage(
                mapOf(
                    "@event_name" to "oppdrag_kvittering",
                    "@id" to UUID.randomUUID(),
                    "@opprettet" to LocalDateTime.now(),
                    "payload" to message
                )
            ).toJson()
        )
    }
}
