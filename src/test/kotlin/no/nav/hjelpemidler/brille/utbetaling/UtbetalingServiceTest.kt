package no.nav.hjelpemidler.brille.utbetaling

import io.kotest.matchers.date.shouldBeAfter
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import no.nav.hjelpemidler.brille.Configuration
import no.nav.hjelpemidler.brille.nare.evaluering.Evalueringer
import no.nav.hjelpemidler.brille.sats.SatsType
import no.nav.hjelpemidler.brille.vedtak.Behandlingsresultat
import no.nav.hjelpemidler.brille.vedtak.Vedtak
import no.nav.hjelpemidler.brille.vilkarsvurdering.Vilkårsvurdering
import org.junit.jupiter.api.Test
import java.time.LocalDate

class UtbetalingServiceTest {

    private val utbetalingStore = mockk<UtbetalingStore>()
    val utbetalingService = UtbetalingService(utbetalingStore, Configuration.utbetalingProperties)

    @Test
    internal fun `registrer ny utbetaling, send til utbetaling, og sett til utbetalt`() {
        every {
            utbetalingStore.lagreUtbetaling(any())
        } answers {
            firstArg<Utbetaling>().copy(id = 1L)
        }

        every {
            utbetalingStore.oppdaterStatus(any())
        } answers {
            firstArg()
        }

        val sats = SatsType.SATS_1
        val etInnvilgetVedtak = Vedtak(
            id = 123456,
            fnrBarn = "12121320922",
            fnrInnsender = "11080642360",
            orgnr = "127627797",
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

        val nyUtbetaling = utbetalingService.opprettNyUtbetaling(vedtak = etInnvilgetVedtak)
        nyUtbetaling.vedtakId shouldBe etInnvilgetVedtak.id
        nyUtbetaling.status shouldBe UtbetalingStatus.NY

        val sendUtbetaling = utbetalingService.sendTilUtbetaling(nyUtbetaling)
        sendUtbetaling.status shouldBe UtbetalingStatus.TIL_UTBETALING
        sendUtbetaling.oppdatert shouldBeAfter nyUtbetaling.oppdatert

        val utbetalt = utbetalingService.settTilUtbetalt(sendUtbetaling)
        utbetalt.status shouldBe UtbetalingStatus.UTBETALT
        utbetalt.vedtakId shouldBe etInnvilgetVedtak.id
        utbetalt.oppdatert shouldBeAfter sendUtbetaling.oppdatert
    }
}
