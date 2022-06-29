package no.nav.hjelpemidler.brille.kafka

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonMapperBuilder
import mu.KotlinLogging
import no.nav.hjelpemidler.brille.Configuration
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.TimeUnit

private val log = KotlinLogging.logger {}

class KafkaProducer(
    private val kafkaProducer: KafkaProducer<String, String>,
    kafkaProperties: Configuration.KafkaProperties = Configuration.kafkaProperties,
) {
    private val topic = kafkaProperties.topic
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
            mapper.writeValueAsString(
                mapOf(
                    "eventId" to UUID.randomUUID(),
                    "eventName" to "hm-bigquery-sink-hendelse",
                    "schemaId" to "hendelse_v2",
                    "payload" to mapOf(
                        "opprettet" to LocalDateTime.now(),
                        "navn" to measurement,
                        "kilde" to "hm-brille-api",
                        "data" to fields.mapValues { it.value.toString() }
                            .plus(tags)
                            .filterKeys { it != "counter" }
                    )
                )
            )
        )
    }

    fun produceEvent(key: String, event: String) {
        try {
            kafkaProducer.send(ProducerRecord(topic, key, event)).get(10, TimeUnit.SECONDS)
        } catch (e: Exception) {
            log.error("Sending av event til $topic feilet")
            throw RuntimeException(e)
        }
    }

    internal data class BrilleVedtakData(
        val fnrBarn: String,
        val optikerOrgNr: String,
        val eventId: UUID,
        val eventName: String,
        val opprettetDato: LocalDateTime = LocalDateTime.now()
    )
}
