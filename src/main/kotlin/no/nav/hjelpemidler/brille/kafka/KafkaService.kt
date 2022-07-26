package no.nav.hjelpemidler.brille.kafka

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonMapperBuilder
import mu.KotlinLogging
import no.nav.hjelpemidler.brille.Configuration
import no.nav.hjelpemidler.brille.avtale.Avtale
import no.nav.hjelpemidler.brille.sats.Brilleseddel
import no.nav.hjelpemidler.brille.vedtak.Behandlingsresultat
import no.nav.hjelpemidler.brille.vedtak.KravDto
import no.nav.hjelpemidler.brille.vedtak.Vedtak
import no.nav.hjelpemidler.brille.vilkarsvurdering.Vilkårsgrunnlag
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerRecord
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.reflect.full.findAnnotation

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

    fun avtaleOpprettet(avtale: Avtale) {
        // todo -> send til tss-sink
        sendTilBigQuery(
            avtale.orgnr,
            AvtaleStatistikk(
                orgnr = avtale.orgnr,
                navn = avtale.navn,
                opprettet = requireNotNull(avtale.opprettet),
            )
        )
    }

    fun vilkårVurdert() {
        // todo -> send til bq-sink
    }

    fun vedtakFattet(krav: KravDto, vedtak: Vedtak<Vilkårsgrunnlag>) {
        // journalfør krav/vedtak som dokument i joark på barnet
        produceEvent(
            vedtak.fnrBarn,
            VedtakOpprettet(
                opprettetDato = vedtak.opprettet,
                fnr = vedtak.fnrBarn,
                brukersNavn = krav.brukersNavn,
                orgnr = vedtak.orgnr,
                orgNavn = krav.orgNavn,
                orgAdresse = krav.orgAdresse,
                navnAvsender = "", // todo -> hvilket navn skal dette egentlig være? navnet til innbygger (barn) eller optiker?
                sakId = vedtak.id.toString(),
                brilleseddel = krav.vilkårsgrunnlag.brilleseddel,
                bestillingsdato = vedtak.bestillingsdato,
                bestillingsreferanse = vedtak.bestillingsreferanse,
                satsBeløp = vedtak.satsBeløp,
                satsBeskrivelse = vedtak.satsBeskrivelse,
                beløp = vedtak.beløp
            )
        )
        sendTilBigQuery(
            vedtak.fnrBarn,
            VedtakStatistikk(
                opprettet = vedtak.opprettet,
                orgnr = vedtak.orgnr,
                orgNavn = krav.orgNavn,
                barnetsAlder = vedtak.vilkårsvurdering.grunnlag.barnetsAlderPåBestillingsdato,
                brilleseddel = krav.vilkårsgrunnlag.brilleseddel,
                bestillingsdato = vedtak.bestillingsdato,
                brillepris = vedtak.brillepris,
                behandlingsresultat = vedtak.behandlingsresultat,
                sats = vedtak.sats.sats.toString(),
                satsBeløp = vedtak.satsBeløp,
                satsBeskrivelse = vedtak.satsBeskrivelse,
                beløp = vedtak.beløp,
            )
        )
    }

    private fun <T> produceEvent(key: String, event: T) {
        try {
            val record = ProducerRecord(topic, key, mapper.writeValueAsString(event))
            kafkaProducer.send(record).get(10, TimeUnit.SECONDS)
        } catch (e: Exception) {
            log.error(e) { "Sending av event til '$topic' feilet" }
            throw KafkaException("Sending av event til '$topic' feilet", e)
        }
    }

    private fun <T : Any> sendTilBigQuery(key: String, payload: T) {
        val bigQueryHendelse = requireNotNull(payload::class.findAnnotation<BigQueryHendelse>()) {
            "${payload::class} mangler BigQueryHendelse-annotasjon"
        }
        produceEvent(
            key,
            mapOf(
                "eventId" to UUID.randomUUID(),
                "eventName" to bigQueryHendelse.eventName,
                "schemaId" to bigQueryHendelse.schemaId,
                "payload" to payload
            )
        )
    }

    internal data class VedtakOpprettet(
        val eventId: UUID = UUID.randomUUID(),
        val eventName: String = "hm-barnebrillevedtak-opprettet",
        val opprettetDato: LocalDateTime,
        val fnr: String,
        val brukersNavn: String,
        val orgnr: String,
        val orgNavn: String,
        val orgAdresse: String,
        val navnAvsender: String,
        val sakId: String,
        val brilleseddel: Brilleseddel,
        val bestillingsdato: LocalDate,
        val bestillingsreferanse: String,
        val satsBeløp: Int,
        val satsBeskrivelse: String,
        val beløp: BigDecimal,
    )

    private annotation class BigQueryHendelse(
        val eventName: String = "hm-bigquery-sink-hendelse",
        val schemaId: String,
    )

    @BigQueryHendelse(schemaId = "avtale_v1")
    data class AvtaleStatistikk(
        val orgnr: String,
        val navn: String,
        val opprettet: LocalDateTime,
    )

    @BigQueryHendelse(schemaId = "vedtak_v1")
    internal data class VedtakStatistikk(
        val opprettet: LocalDateTime,
        val orgnr: String,
        val orgNavn: String,
        val barnetsAlder: Int?,
        val brilleseddel: Brilleseddel,
        val bestillingsdato: LocalDate,
        val brillepris: BigDecimal,
        val behandlingsresultat: Behandlingsresultat,
        val sats: String,
        val satsBeløp: Int,
        val satsBeskrivelse: String,
        val beløp: BigDecimal,
    )

    class KafkaException(message: String, cause: Throwable?) : RuntimeException(message, cause)
}
