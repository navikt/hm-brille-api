package no.nav.hjelpemidler.brille.vilkarsvurdering

import mu.KotlinLogging
import no.nav.hjelpemidler.brille.db.VedtakStore
import no.nav.hjelpemidler.brille.medlemskap.MedlemskapBarn
import no.nav.hjelpemidler.brille.nare.spesifikasjon.Spesifikasjon
import no.nav.hjelpemidler.brille.pdl.PdlClient

private val log = KotlinLogging.logger {}

class VilkårsvurderingService(
    private val vedtakStore: VedtakStore,
    private val pdlClient: PdlClient,
    private val medlemskapBarn: MedlemskapBarn
) {

    suspend fun vurderVilkårBrille(vilkårsgrunnlagDto: VilkårsgrunnlagDto): VilkårsvurderingResultat<Vilkår_v1.Grunnlag_v1> {
        val vedtakForBruker = vedtakStore.hentVedtakForBruker(vilkårsgrunnlagDto.fnrBruker)
        val pdlResponse = pdlClient.hentPerson(vilkårsgrunnlagDto.fnrBruker)
        val medlemskapResultat = medlemskapBarn.sjekkMedlemskapBarn(vilkårsgrunnlagDto.fnrBruker)

        val grunnlag = Vilkår_v1.Grunnlag_v1(
            vedtakForBruker = vedtakForBruker,
            pdlOppslagBruker = pdlResponse,
            beregnSats = vilkårsgrunnlagDto.beregnSats.tilBeregnSats(),
            bestillingsdato = vilkårsgrunnlagDto.bestillingsdato,
            medlemskapResultat = medlemskapResultat
        )
        return vurderVilkår(grunnlag, Vilkår_v1.Brille_v1)
    }

    fun <T> vurderVilkår(grunnlag: T, spesifikasjon: Spesifikasjon<T>): VilkårsvurderingResultat<T> {
        return VilkårsvurderingResultat(grunnlag, spesifikasjon.evaluer(grunnlag))
    }
}
