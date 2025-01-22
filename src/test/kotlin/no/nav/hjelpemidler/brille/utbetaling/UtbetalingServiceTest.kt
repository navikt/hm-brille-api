package no.nav.hjelpemidler.brille.utbetaling

import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import no.nav.hjelpemidler.brille.db.MockDatabaseContext
import no.nav.hjelpemidler.brille.db.transaction
import no.nav.hjelpemidler.brille.sats.SatsType
import no.nav.hjelpemidler.brille.vedtak.Behandlingsresultat
import no.nav.hjelpemidler.brille.vedtak.KravKilde
import no.nav.hjelpemidler.brille.vedtak.Vedtak
import no.nav.hjelpemidler.brille.vilkarsvurdering.Vilkårsvurdering
import no.nav.hjelpemidler.nare.evaluering.Evalueringer
import org.junit.jupiter.api.Test
import java.time.LocalDate

class UtbetalingServiceTest {
    @Test
    fun `registrer ny utbetaling, send til utbetaling, og sett til utbetalt`() = runTest {
        val databaseContext = MockDatabaseContext()
        val utbetalingService = UtbetalingService(databaseContext, mockk(relaxed = true))

        every {
            databaseContext.utbetalingStore.lagreUtbetaling(any())
        } answers {
            firstArg<Utbetaling>().copy(id = 1L)
        }

        every {
            databaseContext.utbetalingStore.oppdaterStatus(any())
        } answers {
            firstArg()
        }

        val sats = SatsType.SATS_1
        val etInnvilgetVedtak = Vedtak(
            id = 123456,
            fnrBarn = "12121320922",
            fnrInnsender = "11080642360",
            navnInnsender = "Kronjuvel Sedat",
            orgnr = "127627791",
            butikkId = null,
            bestillingsdato = LocalDate.now(),
            brillepris = sats.beløp(LocalDate.now()).toBigDecimal(),
            bestillingsreferanse = "test",
            vilkårsvurdering = Vilkårsvurdering("test", Evalueringer().ja("test")),
            behandlingsresultat = Behandlingsresultat.INNVILGET,
            sats = sats,
            satsBeløp = sats.beløp(LocalDate.now()),
            satsBeskrivelse = sats.beskrivelse,
            beløp = sats.beløp(LocalDate.now()).toBigDecimal(),
            kilde = KravKilde.KRAV_APP,
        )

        val nyUtbetaling = utbetalingService.opprettNyUtbetaling(vedtak = etInnvilgetVedtak)
        nyUtbetaling.vedtakId shouldBe etInnvilgetVedtak.id
        nyUtbetaling.status shouldBe UtbetalingStatus.NY

        every {
            databaseContext.utbetalingStore.hentUtbetalingerMedBatchId(nyUtbetaling.batchId)
        } answers {
            listOf(nyUtbetaling)
        }

        every {
            databaseContext.utbetalingStore.lagreUtbetalingsbatch(any())
        } answers {
            1
        }

        every {
            databaseContext.tssIdentStore.hentTssIdent(any())
        } answers {
            "1234"
        }

        val batchList = utbetalingService.hentUtbetalingerMedBatchId(nyUtbetaling.batchId).toUtbetalingBatchList()
        batchList.forEach {
            val tssIdent = transaction(databaseContext) { ctx -> ctx.tssIdentStore.hentTssIdent(it.orgNr) }!!
            utbetalingService.sendBatchTilUtbetaling(it, tssIdent)
        }
    }
}
