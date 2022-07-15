package no.nav.hjelpemidler.brille.vilkarsvurdering

import mu.KotlinLogging
import no.nav.hjelpemidler.brille.Configuration
import no.nav.hjelpemidler.brille.medlemskap.MedlemskapBarn
import no.nav.hjelpemidler.brille.nare.spesifikasjon.Spesifikasjon
import no.nav.hjelpemidler.brille.pdl.PdlClient
import no.nav.hjelpemidler.brille.vedtak.VedtakStore
import java.time.LocalDate

private val log = KotlinLogging.logger {}

class VilkårsvurderingService(
    private val vedtakStore: VedtakStore,
    private val pdlClient: PdlClient,
    private val medlemskapBarn: MedlemskapBarn,
    private val dagensDatoFactory: () -> LocalDate = { LocalDate.now() },
) {
    suspend fun vurderVilkårBrille(vilkårsgrunnlagDto: VilkårsgrunnlagDto): Vilkårsvurdering<Vilkårsgrunnlag> {
        val vedtakForBruker = vedtakStore.hentVedtakForBarn(vilkårsgrunnlagDto.fnrBarn)
        val pdlOppslagBruker = pdlClient.hentPerson(vilkårsgrunnlagDto.fnrBarn)
        val medlemskapResultat =
            medlemskapBarn.sjekkMedlemskapBarn(vilkårsgrunnlagDto.fnrBarn, vilkårsgrunnlagDto.bestillingsdato)
        val vilkårsgrunnlag = Vilkårsgrunnlag(
            vedtakForInnbygger = vedtakForBruker,
            pdlOppslagInnbygger = pdlOppslagBruker,
            medlemskapResultat = medlemskapResultat,
            brilleseddel = vilkårsgrunnlagDto.brilleseddel.tilBrilleseddel(),
            bestillingsdato = vilkårsgrunnlagDto.bestillingsdato,
            dagensDato = dagensDatoFactory(),
        )
        val vilkårsvurdering = vurderVilkår(vilkårsgrunnlag, Vilkårene.Brille)
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
