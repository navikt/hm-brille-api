package no.nav.hjelpemidler.brille.vedtak

import mu.KotlinLogging
import no.nav.hjelpemidler.brille.db.VedtakStore
import no.nav.hjelpemidler.brille.nare.evaluering.Resultat
import no.nav.hjelpemidler.brille.vilkarsvurdering.SøknadDto
import no.nav.hjelpemidler.brille.vilkarsvurdering.VilkårsvurderingService
import java.time.LocalDateTime

private val sikkerLog = KotlinLogging.logger("tjenestekall")

class VedtakService(
    private val vedtakStore: VedtakStore,
    private val vilkårsvurderingService: VilkårsvurderingService
) {

    suspend fun lagVedtak(søknadDto: SøknadDto, fnrInnsender: String): Vedtak_v2 {
        val resultat = vilkårsvurderingService.vurderSøknad(søknadDto)

        if (resultat.evaluering.resultat == Resultat.JA) {
            val opprettet = LocalDateTime.now()
            return vedtakStore.lagreVedtak(
                Vedtak_v2(
                    id = -1,
                    fnrBruker = søknadDto.vilkårsgrunnlagDto.fnrBruker,
                    fnrInnsender = fnrInnsender,
                    orgnr = søknadDto.vilkårsgrunnlagDto.orgnr,
                    bestillingsdato = søknadDto.vilkårsgrunnlagDto.bestillingsdato,
                    brillepris = søknadDto.vilkårsgrunnlagDto.brillepris,
                    bestillingsref = søknadDto.bestillingsreferanse,
                    vilkarsvurdering = resultat,
                    status = "INNVILGET",
                    opprettet = opprettet
                )
            )
        }

        sikkerLog.info { "Vilkårsvurdering ga uventet resultat: $resultat" }
        throw IllegalStateException("Vilkårsvurdering ga uventet resultat")
    }
}
