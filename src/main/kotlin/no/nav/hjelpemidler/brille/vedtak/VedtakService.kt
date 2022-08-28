package no.nav.hjelpemidler.brille.vedtak

import mu.KotlinLogging
import no.nav.hjelpemidler.brille.Configuration
import no.nav.hjelpemidler.brille.db.DatabaseContext
import no.nav.hjelpemidler.brille.db.transaction
import no.nav.hjelpemidler.brille.kafka.KafkaService
import no.nav.hjelpemidler.brille.nare.evaluering.Resultat
import no.nav.hjelpemidler.brille.sats.SatsKalkulator
import no.nav.hjelpemidler.brille.vilkarsvurdering.Vilkårsgrunnlag
import no.nav.hjelpemidler.brille.vilkarsvurdering.VilkårsvurderingException
import no.nav.hjelpemidler.brille.vilkarsvurdering.VilkårsvurderingService
import org.slf4j.LoggerFactory
import java.time.LocalDateTime

private val sikkerLog = KotlinLogging.logger("tjenestekall")

class VedtakService(
    val databaseContext: DatabaseContext,
    private val vilkårsvurderingService: VilkårsvurderingService,
    private val kafkaService: KafkaService,
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

        return transaction(databaseContext) { ctx ->
            val vedtak = ctx.vedtakStore.lagreVedtak(
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
            ctx.vedtakStore.lagreVedtakIKø(vedtak.id, vedtak.opprettet)
            kafkaService.vedtakFattet(krav = krav, vedtak = vedtak)
            if (vilkårsvurdering.grunnlag.medlemskapResultat.medlemskapBevist) {
                kafkaService.medlemskapFolketrygdenBevist(vilkårsgrunnlag.fnrBarn, vedtak.id)
            } else if (vilkårsvurdering.grunnlag.medlemskapResultat.uavklartMedlemskap) {
                kafkaService.medlemskapFolketrygdenAntatt(vilkårsgrunnlag.fnrBarn, vedtak.id)
            }
            vedtak
        }
    }

    suspend fun hentVedtakForUtbetaling(opprettet: LocalDateTime): List<Vedtak<Vilkårsgrunnlag>> {
        return transaction(databaseContext) { ctx ->
            ctx.vedtakStore.hentVedtakForUtbetaling(opprettet)
        }
    }

    suspend fun fjernFraVedTakKø(vedtakList: List<Vedtak<*>>) {
        return transaction(databaseContext) { ctx ->
            vedtakList.forEach {
                ctx.vedtakStore.fjernFraVedTakKø(it.id)
            }
        }
    }

    suspend fun fjernFraVedTakKø(vedtak: Vedtak<*>) {
        return transaction(databaseContext) { ctx ->
            ctx.vedtakStore.fjernFraVedTakKø(vedtak.id)
        }
    }
}
