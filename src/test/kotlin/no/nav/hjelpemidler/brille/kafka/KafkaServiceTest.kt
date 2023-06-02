package no.nav.hjelpemidler.brille.kafka

import io.mockk.every
import io.mockk.mockk
import no.nav.helse.rapids_rivers.KafkaRapid
import no.nav.hjelpemidler.brille.nare.evaluering.Evalueringer
import no.nav.hjelpemidler.brille.sats.Brilleseddel
import no.nav.hjelpemidler.brille.sats.SatsType
import no.nav.hjelpemidler.brille.vedtak.Behandlingsresultat
import no.nav.hjelpemidler.brille.vedtak.KravDto
import no.nav.hjelpemidler.brille.vedtak.KravKilde
import no.nav.hjelpemidler.brille.vedtak.Vedtak
import no.nav.hjelpemidler.brille.vilkarsvurdering.Vilkårsgrunnlag
import no.nav.hjelpemidler.brille.vilkarsvurdering.VilkårsgrunnlagDto
import no.nav.hjelpemidler.brille.vilkarsvurdering.VilkårsgrunnlagExtrasDto
import no.nav.hjelpemidler.brille.vilkarsvurdering.Vilkårsvurdering
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import java.math.BigDecimal
import java.time.LocalDate

internal class KafkaServiceTest {
    private val kafkaRapid = mockk<KafkaRapid>()
    private val service = KafkaService(kafkaRapid)

    @Test
    internal fun `vedtak fattet`() {

        every {
            kafkaRapid.publishWithTimeout(any(), any(), any())
            kafkaRapid.publishWithTimeout(any(), any())
        }

        val sats = SatsType.SATS_1
        val krav = KravDto(
            VilkårsgrunnlagDto(
                orgnr = "067234162",
                fnrBarn = "07083440346",
                brilleseddel = Brilleseddel(
                    høyreSfære = 1.0,
                    høyreSylinder = 1.0,
                    venstreSfære = 1.0,
                    venstreSylinder = 1.0
                ),
                bestillingsdato = LocalDate.now(),
                brillepris = BigDecimal.ZERO,
                extras = VilkårsgrunnlagExtrasDto("", "")
            ),
            "test",
            "test",
            "test",
            "test"
        )
        val vedtak = Vedtak<Vilkårsgrunnlag>(
            fnrBarn = krav.vilkårsgrunnlag.fnrBarn,
            fnrInnsender = "28102525703",
            navnInnsender = "Kronjuvel Sedat",
            orgnr = krav.vilkårsgrunnlag.orgnr,
            bestillingsdato = krav.vilkårsgrunnlag.bestillingsdato,
            brillepris = krav.vilkårsgrunnlag.brillepris,
            bestillingsreferanse = krav.bestillingsreferanse,
            vilkårsvurdering = Vilkårsvurdering(mockk(relaxed = true), Evalueringer().ja("test")),
            behandlingsresultat = Behandlingsresultat.INNVILGET,
            sats = sats,
            satsBeløp = sats.beløp(LocalDate.now()),
            satsBeskrivelse = sats.beskrivelse,
            beløp = sats.beløp(LocalDate.now()).toBigDecimal(),
            kilde = KravKilde.KRAV_APP,
        )

        assertDoesNotThrow {
            service.vedtakFattet(
                krav = krav,
                vedtak = vedtak
            )
        }
    }
}
