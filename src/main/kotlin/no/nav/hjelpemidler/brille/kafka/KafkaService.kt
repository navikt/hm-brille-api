package no.nav.hjelpemidler.brille.kafka

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.annotation.JsonNaming
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonMapperBuilder
import mu.KotlinLogging
import no.nav.hjelpemidler.brille.Configuration
import no.nav.hjelpemidler.brille.avtale.Avtale
import no.nav.hjelpemidler.brille.nare.evaluering.Resultat
import no.nav.hjelpemidler.brille.sats.Brilleseddel
import no.nav.hjelpemidler.brille.vedtak.Behandlingsresultat
import no.nav.hjelpemidler.brille.vedtak.KravDto
import no.nav.hjelpemidler.brille.vedtak.Vedtak
import no.nav.hjelpemidler.brille.vilkarsvurdering.Vilkårsgrunnlag
import no.nav.hjelpemidler.brille.vilkarsvurdering.VilkårsgrunnlagDto
import no.nav.hjelpemidler.brille.vilkarsvurdering.Vilkårsvurdering
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

    fun avtaleOppdatert() {
        // todo -> send til tss-sink
    }

    fun vilkårVurdert() {
        // todo -> send til bq-sink
    }

    suspend fun vilkårIkkeOppfylt(vilkårsgrunnlag: VilkårsgrunnlagDto, vilkårsvurdering: Vilkårsvurdering<Vilkårsgrunnlag>) {
        fun Vilkårsvurdering<Vilkårsgrunnlag>.harResultatJaForVilkår(identifikator: String) =
            this.evaluering.barn.find { it.identifikator == identifikator }!!.resultat == Resultat.JA

        try {
            sendTilBigQuery(
                null,
                AvslagStatistikk(
                    orgnr = vilkårsgrunnlag.orgnr,
                    navn = vilkårsgrunnlag.orgNavn,
                    harIkkeVedtakIKalenderåretOppfylt = vilkårsvurdering.harResultatJaForVilkår("HarIkkeVedtakIKalenderåret v1"),
                    under18ÅrPåBestillingsdatoOppfylt = vilkårsvurdering.harResultatJaForVilkår("Under18ÅrPåBestillingsdato v1"),
                    medlemAvFolketrygdenOppfylt = vilkårsvurdering.harResultatJaForVilkår("MedlemAvFolketrygden v1"),
                    brillestyrkeOppfylt = vilkårsvurdering.harResultatJaForVilkår("Brillestyrke v1"),
                    bestillingsdatoOppfylt = vilkårsvurdering.harResultatJaForVilkår("Bestillingsdato v1"),
                    bestillingsdatoTilbakeITidOppfylt = vilkårsvurdering.harResultatJaForVilkår("BestillingsdatoTilbakeITid v1"),
                    opprettet = LocalDateTime.now()
                )
            )
        } catch (e: Exception) {
            log.error(e) { "Feil under sending av statistikk til BigQuery" }
        }
    }

    fun vedtakFattet(krav: KravDto, vedtak: Vedtak<Vilkårsgrunnlag>) {
        val fnrBarn = vedtak.fnrBarn
        val brilleseddel = krav.vilkårsgrunnlag.brilleseddel
        // journalfør krav/vedtak som dokument i joark på barnet
        produceEvent(
            fnrBarn,
            VedtakOpprettet(
                opprettetDato = vedtak.opprettet,
                fnr = fnrBarn,
                brukersNavn = krav.brukersNavn,
                orgnr = vedtak.orgnr,
                orgNavn = krav.orgNavn,
                orgAdresse = krav.orgAdresse,
                navnAvsender = "", // todo -> slett
                sakId = vedtak.id.toString(),
                brilleseddel = brilleseddel,
                bestillingsdato = vedtak.bestillingsdato,
                bestillingsreferanse = vedtak.bestillingsreferanse,
                satsBeløp = vedtak.satsBeløp,
                satsBeskrivelse = vedtak.satsBeskrivelse,
                beløp = vedtak.beløp
            )
        )
        sendTilBigQuery(
            fnrBarn,
            VedtakStatistikk(
                opprettet = vedtak.opprettet,
                orgnr = vedtak.orgnr,
                orgNavn = krav.orgNavn,
                barnetsAlder = vedtak.vilkårsvurdering.grunnlag.barnetsAlderPåBestillingsdato,
                høyreSfære = brilleseddel.høyreSfære,
                høyreSylinder = brilleseddel.høyreSylinder,
                venstreSfære = brilleseddel.venstreSfære,
                venstreSylinder = brilleseddel.venstreSylinder,
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

    private fun <T> produceEvent(key: String?, event: T) {
        try {
            val record = ProducerRecord(topic, key, mapper.writeValueAsString(event))
            kafkaProducer.send(record).get(10, TimeUnit.SECONDS)
        } catch (e: Exception) {
            log.error(e) { "Sending av event til '$topic' feilet" }
            throw KafkaException("Sending av event til '$topic' feilet", e)
        }
    }

    private fun <T : Any> sendTilBigQuery(key: String?, payload: T) {
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

    /**
     * Lager navn på nøkler i JSON som er kompatible med direkte insert i BigQuery
     * e.g. blir høyreSfære -> hoyre_sfere
     */
    internal class BigQueryStrategy : SnakeCaseStrategy() {
        override fun translate(input: String?): String? {
            val translated = super.translate(input) ?: return input
            return translated
                .replace('æ', 'e')
                .replace('ø', 'o')
                .replace('å', 'a')
        }
    }

    @JsonNaming(BigQueryStrategy::class)
    internal annotation class BigQueryHendelse(
        val eventName: String = "hm-bigquery-sink-hendelse",
        val schemaId: String,
    )

    @JsonNaming(BigQueryStrategy::class)
    @BigQueryHendelse(schemaId = "avtale_v1")
    data class AvtaleStatistikk(
        val orgnr: String,
        val navn: String,
        val opprettet: LocalDateTime,
    )

    @JsonNaming(BigQueryStrategy::class)
    @BigQueryHendelse(schemaId = "vedtak_v1")
    internal data class VedtakStatistikk(
        val opprettet: LocalDateTime,
        val orgnr: String,
        val orgNavn: String,
        val barnetsAlder: Int?,
        val høyreSfære: Double,
        val høyreSylinder: Double,
        val venstreSfære: Double,
        val venstreSylinder: Double,
        val bestillingsdato: LocalDate,
        val brillepris: BigDecimal,
        val behandlingsresultat: Behandlingsresultat,
        val sats: String,
        val satsBeløp: Int,
        val satsBeskrivelse: String,
        val beløp: BigDecimal,
    )

    @JsonNaming(BigQueryStrategy::class)
    @BigQueryHendelse(schemaId = "avslag_v1")
    internal data class AvslagStatistikk(
        val orgnr: String,
        val navn: String,
        val harIkkeVedtakIKalenderåretOppfylt: Boolean,
        val under18ÅrPåBestillingsdatoOppfylt: Boolean,
        val medlemAvFolketrygdenOppfylt: Boolean,
        val brillestyrkeOppfylt: Boolean,
        val bestillingsdatoOppfylt: Boolean,
        val bestillingsdatoTilbakeITidOppfylt: Boolean,
        val opprettet: LocalDateTime
    )

    class KafkaException(message: String, cause: Throwable?) : RuntimeException(message, cause)
}
