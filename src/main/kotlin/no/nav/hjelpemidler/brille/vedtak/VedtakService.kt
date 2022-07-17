package no.nav.hjelpemidler.brille.vedtak

import mu.KotlinLogging
import no.nav.hjelpemidler.brille.Configuration
import no.nav.hjelpemidler.brille.kafka.KafkaService
import no.nav.hjelpemidler.brille.nare.evaluering.Resultat
import no.nav.hjelpemidler.brille.sats.SatsKalkulator
import no.nav.hjelpemidler.brille.vilkarsvurdering.Vilkårsgrunnlag
import no.nav.hjelpemidler.brille.vilkarsvurdering.VilkårsvurderingException
import no.nav.hjelpemidler.brille.vilkarsvurdering.VilkårsvurderingService
import java.util.UUID

private val sikkerLog = KotlinLogging.logger("tjenestekall")

class VedtakService(
    private val vedtakStore: VedtakStore,
    private val vilkårsvurderingService: VilkårsvurderingService,
    private val kafkaService: KafkaService,
) {
    suspend fun lagVedtak(søknadDto: SøknadDto, fnrInnsender: String): Vedtak<Vilkårsgrunnlag> {
        val vilkårsgrunnlag = søknadDto.vilkårsgrunnlag
        val vilkårsvurdering = vilkårsvurderingService.vurderVilkårBrille(vilkårsgrunnlag)

        // TODO: Fjern prodsjekk
        if (Configuration.profile != Configuration.Profile.PROD && vilkårsvurdering.utfall != Resultat.JA) {
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
                bestillingsreferanse = søknadDto.bestillingsreferanse,
                vilkårsvurdering = vilkårsvurdering,
                behandlingsresultat = Behandlingsresultat.INNVILGET,
                sats = sats,
                satsBeløp = satsBeløp,
                satsBeskrivelse = sats.beskrivelse,
                beløp = minOf(satsBeløp, brillepris),
            )
        )

        // Journalfør søknad/vedtak som dokument i joark på barnet
        kafkaService.produceEvent(
            vedtak.fnrBarn,
            KafkaService.BarnebrilleVedtakData(
                eventId = UUID.randomUUID(),
                eventName = "hm-barnebrillevedtak-opprettet",
                fnr = vedtak.fnrBarn,
                brukersNavn = søknadDto.brukersNavn,
                orgnr = vedtak.orgnr,
                orgNavn = søknadDto.orgNavn,
                orgAdresse = søknadDto.orgAdresse,
                navnAvsender = "", // TODO: hvilket navn skal dette egentlig være? Navnet til innbygger (barn) eller optiker?
                sakId = vedtak.id.toString(),
                brilleseddel = vilkårsgrunnlag.brilleseddel,
                bestillingsdato = vilkårsgrunnlag.bestillingsdato,
                bestillingsreferanse = søknadDto.bestillingsreferanse
            )
        )

        return vedtak
    }
}
