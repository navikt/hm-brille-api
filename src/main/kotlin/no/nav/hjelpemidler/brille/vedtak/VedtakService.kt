package no.nav.hjelpemidler.brille.vedtak

import mu.KotlinLogging
import no.nav.hjelpemidler.brille.Configuration
import no.nav.hjelpemidler.brille.kafka.KafkaService
import no.nav.hjelpemidler.brille.nare.evaluering.Resultat
import no.nav.hjelpemidler.brille.sats.SatsKalkulator
import no.nav.hjelpemidler.brille.utbetaling.UtbetalingService
import no.nav.hjelpemidler.brille.vilkarsvurdering.Vilkårsgrunnlag
import no.nav.hjelpemidler.brille.vilkarsvurdering.VilkårsvurderingException
import no.nav.hjelpemidler.brille.vilkarsvurdering.VilkårsvurderingService
import org.slf4j.LoggerFactory
import java.time.LocalDateTime

private val sikkerLog = KotlinLogging.logger("tjenestekall")

class VedtakService(
    private val vedtakStore: VedtakStore,
    private val vilkårsvurderingService: VilkårsvurderingService,
    private val kafkaService: KafkaService,
    private val utbetalingService: UtbetalingService
) {
    companion object {
        private val LOG = LoggerFactory.getLogger(VedtakService::class.java)
    }

    suspend fun lagVedtak(fnrInnsender: String, krav: KravDto): Vedtak<Vilkårsgrunnlag> {
        val vilkårsgrunnlag = krav.vilkårsgrunnlag
        val vilkårsvurdering = vilkårsvurderingService.vurderVilkår(vilkårsgrunnlag)

        if (vilkårsvurdering.utfall != Resultat.JA) {
            sikkerLog.info {
                "Vilkårsvurderingen ga uventet resultat:\n${vilkårsvurdering.toJson()}"
            }
            if (Configuration.prod) {
                throw VilkårsvurderingException("Vilkårsvurderingen ga uventet resultat")
            }
        }
        val sats = SatsKalkulator(vilkårsgrunnlag.brilleseddel).kalkuler()
        val satsBeløp = sats.beløp
        val brillepris = vilkårsgrunnlag.brillepris

        val vedtak = vedtakStore.lagreVedtak(
            Vedtak(
                fnrBarn = vilkårsgrunnlag.fnrBarn,
                fnrInnsender = fnrInnsender,
                orgnr = vilkårsgrunnlag.orgnr,
                bestillingsdato = vilkårsgrunnlag.bestillingsdato,
                brillepris = brillepris,
                bestillingsreferanse = krav.bestillingsreferanse,
                vilkårsvurdering = vilkårsvurdering,
                behandlingsresultat = Behandlingsresultat.INNVILGET,
                sats = sats,
                satsBeløp = satsBeløp,
                satsBeskrivelse = sats.beskrivelse,
                beløp = minOf(satsBeløp.toBigDecimal(), brillepris),
            )
        )
        kafkaService.vedtakFattet(krav = krav, vedtak = vedtak)
        if (vilkårsvurdering.grunnlag.medlemskapResultat.medlemskapBevist) {
            kafkaService.medlemskapFolketrygdenBevist(vilkårsgrunnlag.fnrBarn, vedtak.id)
        } else if (vilkårsvurdering.grunnlag.medlemskapResultat.uavklartMedlemskap) {
            kafkaService.medlemskapFolketrygdenAntatt(vilkårsgrunnlag.fnrBarn, vedtak.id)
        }
        try {
            if (utbetalingService.isEnabled()) utbetalingService.opprettNyUtbetaling(vedtak)
        } catch (e: Exception) {
            // TODO fjerne try catch når utbetalingservice er stabilt i prod.
            LOG.error("Opprett utbetaling feilet", e)
        }
        return vedtak
    }

    suspend fun hentVedtakIkkeRegistrertForUtbetaling(opprettet: LocalDateTime): List<Vedtak<Vilkårsgrunnlag>> {
        return vedtakStore.hentVedtakIkkeRegistrertForUtbetaling(opprettet)
    }
}
