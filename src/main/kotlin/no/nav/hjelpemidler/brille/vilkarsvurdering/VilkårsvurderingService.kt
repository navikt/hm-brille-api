package no.nav.hjelpemidler.brille.vilkarsvurdering

import mu.KotlinLogging
import no.nav.hjelpemidler.brille.db.VedtakStore
import no.nav.hjelpemidler.brille.nare.spesifikasjon.Spesifikasjon
import no.nav.hjelpemidler.brille.pdl.PdlService

private val log = KotlinLogging.logger {}

class VilkårsvurderingService(private val vedtakStore: VedtakStore, private val pdlService: PdlService) {

    suspend fun vurderVilkårBrille(vilkårsgrunnlagDto: VilkårsgrunnlagDto): VilkårsvurderingResultat<Vilkår_v1.Grunnlag_v1> {
        val eksisterendeVedtak = vedtakStore.hentVedtakIBestillingsdatoAr<Vilkår_v1.Grunnlag_v1>(
            vilkårsgrunnlagDto.fnrBruker,
            vilkårsgrunnlagDto.bestillingsdato
        )
        val personInformasjon = pdlService.hentPerson(vilkårsgrunnlagDto.fnrBruker)
        val grunnlag = Vilkår_v1.Grunnlag_v1(
            eksisterendeVedtak = eksisterendeVedtak?.let {
                Vilkår_v1.EksisterendeVedtak(
                    id = it.id,
                    fnrBruker = it.fnrBruker,
                    bestillingsdato = it.bestillingsdato,
                    status = it.status,
                    opprettet = it.opprettet
                )
            },
            personInformasjon = personInformasjon,
            beregnSats = vilkårsgrunnlagDto.beregnSats.tilBeregnSats(),
            bestillingsdato = vilkårsgrunnlagDto.bestillingsdato
        )
        return vurderVilkår(grunnlag, Vilkår_v1.Brille_v1)
    }

    fun <T> vurderVilkår(grunnlag: T, spesifikasjon: Spesifikasjon<T>): VilkårsvurderingResultat<T> {
        return VilkårsvurderingResultat(grunnlag, spesifikasjon.evaluer(grunnlag))
    }
}
