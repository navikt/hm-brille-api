package no.nav.hjelpemidler.brille.vilkarsvurdering

import mu.KotlinLogging
import no.nav.hjelpemidler.brille.Configuration
import no.nav.hjelpemidler.brille.Profile
import no.nav.hjelpemidler.brille.jsonMapper
import no.nav.hjelpemidler.brille.medlemskap.MedlemskapBarn
import no.nav.hjelpemidler.brille.nare.spesifikasjon.Spesifikasjon
import no.nav.hjelpemidler.brille.pdl.PdlClient
import no.nav.hjelpemidler.brille.vedtak.VedtakStore

private val log = KotlinLogging.logger {}

class VilkårsvurderingService(
    private val vedtakStore: VedtakStore,
    private val pdlClient: PdlClient,
    private val medlemskapBarn: MedlemskapBarn,
) {
    suspend fun vurderVilkårBrille(vilkårsgrunnlagDto: VilkårsgrunnlagDto): VilkårsvurderingResultat<Vilkår_v1.Grunnlag_v1> {
        val vedtakForBruker = vedtakStore.hentVedtakForBruker(vilkårsgrunnlagDto.fnrBruker)
        val pdlOppslagBruker = pdlClient.hentPerson(vilkårsgrunnlagDto.fnrBruker)
        val medlemskapResultat = medlemskapBarn.sjekkMedlemskapBarn(vilkårsgrunnlagDto.fnrBruker)
        val grunnlag = Vilkår_v1.Grunnlag_v1(
            vedtakForBruker = vedtakForBruker,
            pdlOppslagBruker = pdlOppslagBruker,
            beregnSats = vilkårsgrunnlagDto.beregnSats.tilBeregnSats(),
            bestillingsdato = vilkårsgrunnlagDto.bestillingsdato,
            medlemskapResultat = medlemskapResultat
        )
        val vilkarsvurdering = vurderVilkår(grunnlag, Vilkår_v1.Brille_v1)
        log.info {
            val vilkarsvurderingJson = when (Configuration.profile) {
                Profile.LOCAL -> "\n" + jsonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(vilkarsvurdering)
                else -> " " + vilkarsvurdering.utfall
            }
            "Resultat av vilkårsvurdering:$vilkarsvurderingJson"
        }
        return vilkarsvurdering
    }

    fun <T> vurderVilkår(grunnlag: T, spesifikasjon: Spesifikasjon<T>): VilkårsvurderingResultat<T> {
        return VilkårsvurderingResultat(grunnlag, spesifikasjon.evaluer(grunnlag))
    }
}
