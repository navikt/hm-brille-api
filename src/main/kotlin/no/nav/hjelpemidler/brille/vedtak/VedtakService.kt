package no.nav.hjelpemidler.brille.vedtak

import mu.KotlinLogging
import no.nav.hjelpemidler.brille.jsonMapper
import no.nav.hjelpemidler.brille.kafka.KafkaService
import no.nav.hjelpemidler.brille.nare.evaluering.Resultat
import no.nav.hjelpemidler.brille.vilkarsvurdering.Vilkår_v1
import no.nav.hjelpemidler.brille.vilkarsvurdering.VilkårsvurderingService
import java.time.LocalDateTime
import java.util.UUID

private val sikkerLog = KotlinLogging.logger("tjenestekall")

class VedtakService(
    private val vedtakStore: VedtakStore,
    private val vilkårsvurderingService: VilkårsvurderingService,
    private val kafkaService: KafkaService,
) {
    suspend fun lagVedtak(søknadDto: SøknadDto, fnrInnsender: String): Vedtak_v2<Vilkår_v1.Grunnlag_v1> {
        val vilkarsvurdering = vilkårsvurderingService.vurderVilkårBrille(søknadDto.vilkårsgrunnlag)

        if (vilkarsvurdering.utfall != Resultat.JA) {
            sikkerLog.info {
                val vilkarsvurderingJson =
                    jsonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(vilkarsvurdering)
                "Vilkårsvurdering ga uventet resultat:\n$vilkarsvurderingJson"
            }
            throw IllegalStateException("Vilkårsvurdering ga uventet resultat")
        }

        val opprettet = LocalDateTime.now()
        val vedtak = vedtakStore.lagreVedtak(
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

        // Journalfør søknad/vedtak som dokument i joark på barnet
        val barneBrilleVedtakData = KafkaService.BarnebrilleVedtakData(
            fnr = vedtak.fnrBruker,
            orgnr = vedtak.orgnr,
            eventId = UUID.randomUUID(),
            "hm-barnebrillevedtak-opprettet",
            navnAvsender = "Ole Brumm", // TODO: hvilket navn skal dette egentlig være? Navnet til bruker (barn) eller optiker?
            sakId = vedtak.id.toString()
        )
        val event = jsonMapper.writeValueAsString(barneBrilleVedtakData)
        kafkaService.produceEvent(vedtak.fnrBruker, event)

        return vedtak
    }
}
