package no.nav.hjelpemidler.brille.vedtak

import mu.KotlinLogging
import no.nav.hjelpemidler.brille.nare.evaluering.Resultat
import no.nav.hjelpemidler.brille.vilkarsvurdering.Vilkår_v1
import no.nav.hjelpemidler.brille.vilkarsvurdering.VilkårsvurderingService
import java.time.LocalDateTime

private val sikkerLog = KotlinLogging.logger("tjenestekall")

class VedtakService(
    private val vedtakStore: VedtakStore,
    private val vilkårsvurderingService: VilkårsvurderingService,
) {
    suspend fun lagVedtak(søknadDto: SøknadDto, fnrInnsender: String): Vedtak_v2<Vilkår_v1.Grunnlag_v1> {
        val vilkarsvurdering = vilkårsvurderingService.vurderVilkårBrille(søknadDto.vilkårsgrunnlag)

        when (vilkarsvurdering.utfall) {
            Resultat.JA -> {
                val opprettet = LocalDateTime.now()
                return vedtakStore.lagreVedtak(
                    Vedtak_v2(
                        id = -1,
                        fnrBruker = søknadDto.vilkårsgrunnlag.fnrBruker,
                        fnrInnsender = fnrInnsender,
                        orgnr = søknadDto.vilkårsgrunnlag.orgnr,
                        bestillingsdato = søknadDto.vilkårsgrunnlag.bestillingsdato,
                        brillepris = søknadDto.vilkårsgrunnlag.brillepris,
                        bestillingsreferanse = søknadDto.bestillingsreferanse,
                        vilkarsvurdering = vilkarsvurdering,
                        status = "INNVILGET",
                        opprettet = opprettet
                    )
                )
            }
            else -> {
                sikkerLog.info { "Vilkårsvurdering ga uventet resultat: $vilkarsvurdering" }
                throw IllegalStateException("Vilkårsvurdering ga uventet resultat")
            }
        }
    }
}
