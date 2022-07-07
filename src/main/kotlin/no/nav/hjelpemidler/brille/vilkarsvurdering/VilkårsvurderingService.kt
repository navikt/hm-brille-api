package no.nav.hjelpemidler.brille.vilkarsvurdering

import mu.KotlinLogging
import no.nav.hjelpemidler.brille.Configuration
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
    suspend fun vurderVilkårBrille(vilkårsgrunnlagDto: VilkårsgrunnlagDto): Vilkårsvurdering<Vilkårsgrunnlag> {
        val vedtakForBruker = vedtakStore.hentVedtakForBruker(vilkårsgrunnlagDto.fnrBruker)
        val pdlOppslagBruker = pdlClient.hentPerson(vilkårsgrunnlagDto.fnrBruker)
        val medlemskapResultat = medlemskapBarn.sjekkMedlemskapBarn(vilkårsgrunnlagDto.fnrBruker)
        val grunnlag = Vilkårsgrunnlag(
            vedtakForBruker = vedtakForBruker,
            pdlOppslagBruker = pdlOppslagBruker,
            brilleseddel = vilkårsgrunnlagDto.brilleseddel.tilBrilleseddel(),
            bestillingsdato = vilkårsgrunnlagDto.bestillingsdato,
            medlemskapResultat = medlemskapResultat
        )
        val vilkårsvurdering = vurderVilkår(grunnlag, Vilkårene.Brille)
        if (!Configuration.prod) {
            log.info {
                "Resultat av vilkårsvurdering: ${vilkårsvurdering.toJson()}"
            }
        }
        return vilkårsvurdering
    }

    fun <T> vurderVilkår(grunnlag: T, spesifikasjon: Spesifikasjon<T>): Vilkårsvurdering<T> {
        return Vilkårsvurdering(grunnlag, spesifikasjon.evaluer(grunnlag))
    }
}
