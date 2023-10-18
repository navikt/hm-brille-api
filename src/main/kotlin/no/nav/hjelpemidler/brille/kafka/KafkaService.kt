package no.nav.hjelpemidler.brille.kafka

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.annotation.JsonNaming
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonMapperBuilder
import mu.KotlinLogging
import no.nav.helse.rapids_rivers.KafkaRapid
import no.nav.hjelpemidler.brille.avtale.Avtale
import no.nav.hjelpemidler.brille.sats.AmblyopiSatsType
import no.nav.hjelpemidler.brille.sats.Brilleseddel
import no.nav.hjelpemidler.brille.sats.SatsType
import no.nav.hjelpemidler.brille.sats.kalkulator.KalkulatorResultat
import no.nav.hjelpemidler.brille.vedtak.Behandlingsresultat
import no.nav.hjelpemidler.brille.vedtak.KravDto
import no.nav.hjelpemidler.brille.vedtak.KravKilde
import no.nav.hjelpemidler.brille.vedtak.SlettetAvType
import no.nav.hjelpemidler.brille.vedtak.Vedtak
import no.nav.hjelpemidler.brille.vilkarsvurdering.Vilkårsgrunnlag
import no.nav.hjelpemidler.brille.vilkarsvurdering.VilkårsgrunnlagDto
import no.nav.hjelpemidler.brille.vilkarsvurdering.Vilkårsvurdering
import no.nav.hjelpemidler.brille.vilkarsvurdering.harResultatJaForVilkår
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import kotlin.reflect.full.findAnnotation

private val log = KotlinLogging.logger {}

class KafkaService(private val kafkaRapid: KafkaRapid) {

    private val mapper = jacksonMapperBuilder()
        .addModule(JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .serializationInclusion(JsonInclude.Include.NON_NULL)
        .build()

    fun avtaleOpprettet(avtale: Avtale) {
        // Metrics
        sendTilBigQuery(
            avtale.orgnr,
            AvtaleStatistikk(
                orgnr = avtale.orgnr,
                navn = avtale.navn,
                opprettet = requireNotNull(avtale.opprettet),
            ),
        )

        // Oppdater TSS-registeret med kontonr slik at betaling kan finne frem til dette
        // TODO: Vurder om null-sjekken under er nødvendig og garanter at man blir eventually consistent
        avtale.kontonr?.let { oppdaterTSS(avtale.orgnr, avtale.kontonr) }
            ?: log.info("TSS ikke oppdatert ved opprettelse av oppgave da kontonr mangler i datamodellen")
    }

    fun avtaleOppdatert(avtale: Avtale) {
        // Oppdater TSS-registeret med kontonr slik at betaling kan finne frem til dette
        // TODO: Vurder om null-sjekken under er nødvendig og garanter at man blir eventually consistent
        avtale.kontonr?.let { oppdaterTSS(avtale.orgnr, avtale.kontonr) }
            ?: log.info("TSS ikke oppdatert ved oppdatering av oppgave da kontonr mangler i datamodellen")
    }

    fun vilkårVurdert() {
        // todo -> send til bq-sink
    }

    fun vilkårIkkeOppfylt(
        vilkårsgrunnlag: VilkårsgrunnlagDto,
        vilkårsvurdering: Vilkårsvurdering<Vilkårsgrunnlag>,
    ) {
        try {
            sendTilBigQuery(
                null,
                AvslagStatistikk(
                    orgnr = vilkårsgrunnlag.orgnr,
                    navn = vilkårsgrunnlag.extras.orgNavn,
                    bestillingsreferanse = vilkårsgrunnlag.extras.bestillingsreferanse,
                    harIkkeVedtakIKalenderåretOppfylt = vilkårsvurdering.harResultatJaForVilkår("HarIkkeVedtakIKalenderåret"),
                    under18ÅrPåBestillingsdatoOppfylt = vilkårsvurdering.harResultatJaForVilkår("Under18ÅrPåBestillingsdato"),
                    medlemAvFolketrygdenOppfylt = vilkårsvurdering.harResultatJaForVilkår("MedlemAvFolketrygden"),
                    brillestyrkeOppfylt = vilkårsvurdering.harResultatJaForVilkår("Brillestyrke"),
                    bestillingsdatoOppfylt = vilkårsvurdering.harResultatJaForVilkår("Bestillingsdato"),
                    bestillingsdatoTilbakeITidOppfylt = vilkårsvurdering.harResultatJaForVilkår("Bestillingsdato"),
                    opprettet = LocalDateTime.now(),
                ),
            )
        } catch (e: Exception) {
            log.error(e) { "Feil under sending av statistikk til BigQuery" }
        }
    }

    fun journalførAvvisning(
        fnrBarn: String,
        navnBarn: String,
        orgnr: String,
        orgNavn: String,
        brilleseddel: Brilleseddel,
        bestillingsdato: LocalDate,
        eksisterendeVedtakDato: LocalDate?,
        årsaker: List<String>,
    ) {
        produceEvent(
            null,
            JournalførAvvisning(
                fnrBarn = fnrBarn,
                navnBarn = navnBarn,
                orgnr = orgnr,
                orgNavn = orgNavn,
                brilleseddel = brilleseddel,
                bestillingsdato = bestillingsdato,
                eksisterendeVedtakDato = eksisterendeVedtakDato,
                årsaker = årsaker,
            ),
        )
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
                beløp = vedtak.beløp,
                kilde = vedtak.kilde,
            ),
        )
        sendTilBigQuery(
            fnrBarn,
            VedtakStatistikk(
                vedtakId = vedtak.id,
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
                bestillingsreferanse = vedtak.bestillingsreferanse,
                kilde = vedtak.kilde,
            ),
        )
    }

    fun medlemskapFolketrygdenBevist(fnrBarn: String, vedtakId: Long = -1) {
        sendTilBigQuery(
            fnrBarn,
            MedlemskapFolketrygdenStatistikk(
                vedtakId = vedtakId,
                bevist = true,
            ),
        )
    }

    fun medlemskapFolketrygdenAntatt(fnrBarn: String, vedtakId: Long = -1) {
        sendTilBigQuery(
            fnrBarn,
            MedlemskapFolketrygdenStatistikk(
                vedtakId = vedtakId,
                antatt = true,
            ),
        )
    }

    fun medlemskapFolketrygdenAvvist(fnrBarn: String) {
        sendTilBigQuery(
            fnrBarn,
            MedlemskapFolketrygdenStatistikk(
                avvist = true,
            ),
        )
    }

    fun vedtakSlettet(vedtakId: Long, slettetAvType: SlettetAvType) {
        sendTilBigQuery(
            null,
            SlettedeVedtakStatistikk(
                vedtakId = vedtakId,
                slettetAvType = slettetAvType,
            ),
        )
    }

    fun kalkulertBrillestøtte(kalkulatorResultat: KalkulatorResultat) {
        sendTilBigQuery(
            null,
            KalkulatorResultatStatistikk(
                brillestøtte = kalkulatorResultat.brillestøtte.sats != SatsType.INGEN,
                amblyopistøtte = kalkulatorResultat.amblyopistøtte.sats != AmblyopiSatsType.INGEN,
                brillestøttesats = kalkulatorResultat.brillestøtte.sats.name,
                amblyopistøttesats = kalkulatorResultat.amblyopistøtte.sats.name,
            ),
        )
    }

    fun kliniskDataOpprettet(orgnr: String) {
        sendTilBigQuery(
            orgnr,
            KliniskDataStatistikk(
                orgnr = orgnr,
            ),
        )
    }

    fun <T> produceEvent(key: String?, event: T) {
        try {
            val message = mapper.writeValueAsString(event)
            if (key != null) {
                kafkaRapid.publishWithTimeout(key, message, 10)
            } else {
                kafkaRapid.publishWithTimeout(message, 10)
            }
        } catch (e: Exception) {
            log.error("We got error while sending to kafka", e)
            throw KafkaException("Error while sending to kafka", e)
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
                "payload" to payload,
            ),
        )
    }

    fun oppdaterTSS(orgnr: String, kontonr: String) {
        produceEvent(
            orgnr,
            mapOf(
                "eventId" to UUID.randomUUID(),
                "eventName" to "hm-utbetaling-tss-optiker",
                "orgnr" to orgnr,
                "kontonr" to kontonr,
                "opprettet" to LocalDateTime.now(),
            ),
        )
    }

    fun feilregistrerBarnebrillerIJoark(sakId: Long, joarkRef: Long) {
        produceEvent(
            null,
            mapOf(
                "eventId" to UUID.randomUUID(),
                "eventName" to "hm-barnebriller-feilregistrer-journalpost",
                "sakId" to sakId.toString(),
                "joarkRef" to joarkRef.toString(),
                "opprettet" to LocalDateTime.now(),
            ),
        )
    }

    fun isAlive() = kafkaRapid.isRunning()
    fun isReady() = kafkaRapid.isReady()
    fun isConsumerClosed() = kafkaRapid.isConsumerClosed()
    fun isProducerClosed() = kafkaRapid.isProducerClosed()

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
        val kilde: KravKilde,
    )

    internal data class JournalførAvvisning(
        val eventName: String = "hm-brille-avvisning",
        val eventId: UUID = UUID.randomUUID(),
        val opprettet: LocalDateTime = LocalDateTime.now(),
        val fnrBarn: String,
        val navnBarn: String,
        val orgnr: String,
        val orgNavn: String,
        val brilleseddel: Brilleseddel,
        val bestillingsdato: LocalDate,
        val eksisterendeVedtakDato: LocalDate?,
        val årsaker: List<String>,
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
        val vedtakId: Long,
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
        val bestillingsreferanse: String,
        val kilde: KravKilde,
    )

    @JsonNaming(BigQueryStrategy::class)
    @BigQueryHendelse(schemaId = "avslag_v1")
    internal data class AvslagStatistikk(
        val orgnr: String,
        val navn: String,
        val bestillingsreferanse: String,
        val harIkkeVedtakIKalenderåretOppfylt: Boolean,
        val under18ÅrPåBestillingsdatoOppfylt: Boolean,
        val medlemAvFolketrygdenOppfylt: Boolean,
        val brillestyrkeOppfylt: Boolean,
        val bestillingsdatoOppfylt: Boolean,
        val bestillingsdatoTilbakeITidOppfylt: Boolean,
        val opprettet: LocalDateTime,
    )

    @JsonNaming(BigQueryStrategy::class)
    @BigQueryHendelse(schemaId = "medlemskap_folketrygden_v1")
    internal data class MedlemskapFolketrygdenStatistikk(
        val vedtakId: Long = -1,
        val bevist: Boolean = false,
        val antatt: Boolean = false,
        val avvist: Boolean = false,
        val opprettet: LocalDateTime = LocalDateTime.now(),
    )

    @JsonNaming(BigQueryStrategy::class)
    @BigQueryHendelse(schemaId = "kalkulatorresultat_v1")
    internal data class KalkulatorResultatStatistikk(
        val brillestøtte: Boolean = false,
        val amblyopistøtte: Boolean = false,
        val brillestøttesats: String = SatsType.INGEN.name,
        val amblyopistøttesats: String = AmblyopiSatsType.INGEN.name,
        val opprettet: LocalDateTime = LocalDateTime.now(),
    )

    @JsonNaming(BigQueryStrategy::class)
    @BigQueryHendelse(schemaId = "slettede_vedtak_v1")
    internal data class SlettedeVedtakStatistikk(
        val vedtakId: Long,
        val slettetAvType: SlettetAvType,
        val opprettet: LocalDateTime = LocalDateTime.now(),
    )

    @JsonNaming(BigQueryStrategy::class)
    @BigQueryHendelse(schemaId = "klinisk_data_v1")
    internal data class KliniskDataStatistikk(
        val orgnr: String,
        val opprettet: LocalDateTime = LocalDateTime.now(),
    )

    class KafkaException(message: String, cause: Throwable?) : RuntimeException(message, cause)
}
