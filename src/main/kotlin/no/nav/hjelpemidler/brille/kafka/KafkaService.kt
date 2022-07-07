package no.nav.hjelpemidler.brille.kafka

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonMapperBuilder
import mu.KotlinLogging
import no.nav.hjelpemidler.brille.Configuration
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerRecord
import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.TimeUnit

private val log = KotlinLogging.logger {}

class KafkaService(
    private val topic: String = Configuration.kafkaProperties.topic,
    kafkaProducerFactory: () -> Producer<String, String>,
) {
    private val kafkaProducer = kafkaProducerFactory()
    private val mapper = jacksonMapperBuilder()
        .addModule(JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .serializationInclusion(JsonInclude.Include.NON_NULL)
        .build()

    fun hendelseOpprettet(
        measurement: String,
        fields: Map<String, Any>,
        tags: Map<String, String>,
    ) {
        produceEvent(
            measurement,
            mapOf(
                "eventId" to UUID.randomUUID(),
                "eventName" to "hm-bigquery-sink-hendelse",
                "schemaId" to "TODO",
                "payload" to emptyMap<String, String>()
            )
        )
    }

    fun <T> produceEvent(key: String, event: T) {
        try {
            val record = ProducerRecord(topic, key, mapper.writeValueAsString(event))
            kafkaProducer.send(record).get(10, TimeUnit.SECONDS)
        } catch (e: Exception) {
            log.error("Sending av event til '$topic' feilet")
            throw RuntimeException(e)
        }
    }

    internal data class BarnebrilleVedtakData(
        val fnr: String,
        val orgnr: String,
        val eventId: UUID,
        val eventName: String,
        val opprettetDato: LocalDateTime = LocalDateTime.now(),
        val navnAvsender: String,
        val sakId: String,
    )
}
