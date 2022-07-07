package no.nav.hjelpemidler.brille.vedtak

import mu.KotlinLogging
import no.nav.hjelpemidler.brille.Configuration
import no.nav.hjelpemidler.brille.kafka.KafkaService
import no.nav.hjelpemidler.brille.nare.evaluering.Resultat
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
        val vilkårsvurdering = vilkårsvurderingService.vurderVilkårBrille(søknadDto.vilkårsgrunnlag)

        if (vilkårsvurdering.utfall != Resultat.JA) {
            sikkerLog.info {
                "Vilkårsvurderingen ga uventet resultat:\n${vilkårsvurdering.toJson()}"
            }
            if (Configuration.prod) {
                throw VilkårsvurderingException("Vilkårsvurderingen ga uventet resultat")
            }
        }

        val vedtak = vedtakStore.lagreVedtak(
            Vedtak(
                fnrBruker = søknadDto.vilkårsgrunnlag.fnrBruker,
                fnrInnsender = fnrInnsender,
                orgnr = søknadDto.vilkårsgrunnlag.orgnr,
                bestillingsdato = søknadDto.vilkårsgrunnlag.bestillingsdato,
                brillepris = søknadDto.vilkårsgrunnlag.brillepris,
                bestillingsreferanse = søknadDto.bestillingsreferanse,
                vilkårsvurdering = vilkårsvurdering,
            )
        )

        // Journalfør søknad/vedtak som dokument i joark på barnet
        kafkaService.produceEvent(
            vedtak.fnrBruker,
            KafkaService.BarnebrilleVedtakData(
                fnr = vedtak.fnrBruker,
                orgnr = vedtak.orgnr,
                eventId = UUID.randomUUID(),
                "hm-barnebrillevedtak-opprettet",
                navnAvsender = "Ole Brumm", // TODO: hvilket navn skal dette egentlig være? Navnet til bruker (barn) eller optiker?
                sakId = vedtak.id.toString()
            )
        )

        return vedtak
    }
}
