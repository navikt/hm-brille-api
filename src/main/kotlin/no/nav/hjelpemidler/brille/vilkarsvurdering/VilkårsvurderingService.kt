package no.nav.hjelpemidler.brille.vilkarsvurdering

import mu.KotlinLogging
import no.nav.hjelpemidler.brille.db.VedtakStore
import no.nav.hjelpemidler.brille.nare.evaluering.Evaluering
import no.nav.hjelpemidler.brille.pdl.PdlService
import no.nav.hjelpemidler.brille.sats.SatsGrunnlag
import java.math.BigDecimal
import java.time.LocalDate

private val log = KotlinLogging.logger {}

class VilkårsvurderingService(private val vedtakStore: VedtakStore, private val pdlService: PdlService) {

    suspend fun vurderVilkår(vilkårsgrunnlagDto: VilkårsgrunnlagDto): VilkårsvurderingResultat {
        val vedtakIBestillingsdatoAr =
            vedtakStore.hentVedtakIBestillingsdatoAr(vilkårsgrunnlagDto.fnrBruker, vilkårsgrunnlagDto.bestillingsdato)
        val grunnlag = Vilkår_v1.Grunnlag_v1(
            vedtakIBestillingsdatoAr = vedtakIBestillingsdatoAr,
            personInformasjon = pdlService.hentPerson(vilkårsgrunnlagDto.fnrBruker),
            satsGrunnlag = vilkårsgrunnlagDto.brillestyrke,
            bestillingsdato = vilkårsgrunnlagDto.bestillingsdato
        )

        val evaluering = Vilkår_v1.Brille_v1.evaluer(grunnlag)
        return VilkårsvurderingResultat(grunnlag, evaluering)
    }

    suspend fun vurderSøknad(søknadDto: SøknadDto) = vurderVilkår(søknadDto.vilkårsgrunnlagDto)
}

data class VilkårsgrunnlagDto(
    val orgnr: String,
    val fnrBruker: String,
    val brillestyrke: SatsGrunnlag,
    val bestillingsdato: LocalDate,
    val brillepris: BigDecimal
)

data class SøknadDto(
    val vilkårsgrunnlagDto: VilkårsgrunnlagDto,
    val bestillingsreferanse: String
)

data class VilkårsvurderingResultat(
    val grunnlag: Any, // todo: generisk type
    val evaluering: Evaluering
)
