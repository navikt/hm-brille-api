package no.nav.hjelpemidler.brille.utbetaling

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.hjelpemidler.brille.nare.evaluering.Evalueringer
import no.nav.hjelpemidler.brille.sats.SatsType
import no.nav.hjelpemidler.brille.vedtak.Behandlingsresultat
import no.nav.hjelpemidler.brille.vedtak.Vedtak
import no.nav.hjelpemidler.brille.vedtak.toDto
import no.nav.hjelpemidler.brille.vilkarsvurdering.Vilkårsvurdering
import org.junit.jupiter.api.Test
import java.time.LocalDate

class UtbetalingsBatchTest {

    @Test
    fun `lag utbetalingsbatch`() {
        val sats = SatsType.SATS_1
        val etVedtak = Vedtak(
            id = 1L,
            fnrBarn = "12121320922",
            fnrInnsender = "11080642360",
            navnInnsender = "Kronjuvel Sedat",
            orgnr = "123456789",
            bestillingsdato = LocalDate.now(),
            brillepris = sats.beløp.toBigDecimal(),
            bestillingsreferanse = "test",
            vilkårsvurdering = Vilkårsvurdering("test", Evalueringer().ja("test")),
            behandlingsresultat = Behandlingsresultat.INNVILGET,
            sats = sats,
            satsBeløp = sats.beløp,
            satsBeskrivelse = sats.beskrivelse,
            beløp = sats.beløp.toBigDecimal(),
        )

        val ut1 = Utbetaling(
            vedtakId = etVedtak.id,
            referanse = "ref1",
            utbetalingsdato = LocalDate.now(),
            vedtak = etVedtak.toDto()
        )

        val etVedtak2 = Vedtak(
            id = 2L,
            fnrBarn = "12121320922",
            fnrInnsender = "11080642360",
            navnInnsender = "Kronjuvel Sedat",
            orgnr = "123456789",
            bestillingsdato = LocalDate.now(),
            brillepris = sats.beløp.toBigDecimal(),
            bestillingsreferanse = "test",
            vilkårsvurdering = Vilkårsvurdering("test", Evalueringer().ja("test")),
            behandlingsresultat = Behandlingsresultat.INNVILGET,
            sats = sats,
            satsBeløp = sats.beløp,
            satsBeskrivelse = sats.beskrivelse,
            beløp = sats.beløp.toBigDecimal(),
        )

        val ut2 = Utbetaling(
            vedtakId = etVedtak2.id,
            referanse = "ref1",
            utbetalingsdato = LocalDate.now(),
            vedtak = etVedtak2.toDto()
        )

        val etVedtak3 = Vedtak(
            id = 3L,
            fnrBarn = "12121320922",
            fnrInnsender = "11080642360",
            navnInnsender = "Kronjuvel Sedat",
            orgnr = "987654321",
            bestillingsdato = LocalDate.now(),
            brillepris = sats.beløp.toBigDecimal(),
            bestillingsreferanse = "test",
            vilkårsvurdering = Vilkårsvurdering("test", Evalueringer().ja("test")),
            behandlingsresultat = Behandlingsresultat.INNVILGET,
            sats = sats,
            satsBeløp = sats.beløp,
            satsBeskrivelse = sats.beskrivelse,
            beløp = sats.beløp.toBigDecimal(),
        )

        val ut3 = Utbetaling(
            vedtakId = etVedtak3.id,
            referanse = "ref1",
            utbetalingsdato = LocalDate.now(),
            vedtak = etVedtak3.toDto()
        )

        val utbetalinger = listOf<Utbetaling>(ut1, ut2, ut3)
        val batcher = utbetalinger.toUtbetalingsBatchList()

        batcher.size shouldBe 2
        val batch1 = batcher[0]
        val batch2 = batcher[1]
        batch1.orgNr shouldBe "123456789"
        batch1.utbetalinger.size shouldBe 2
        batch2.utbetalinger.size shouldBe 1
        batch2.orgNr shouldBe "987654321"
        batch1.batchId shouldNotBe batch2.batchId

        batch1.toUtbetalingsBatch().totalbeløp shouldBe "1500".toBigDecimal()
    }
}
