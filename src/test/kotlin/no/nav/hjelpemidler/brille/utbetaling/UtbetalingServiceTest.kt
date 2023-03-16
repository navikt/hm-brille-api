package no.nav.hjelpemidler.brille.utbetaling

import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.hjelpemidler.brille.db.createDatabaseContext
import no.nav.hjelpemidler.brille.db.createDatabaseSessionContextWithMocks
import no.nav.hjelpemidler.brille.db.transaction
import no.nav.hjelpemidler.brille.nare.evaluering.Evalueringer
import no.nav.hjelpemidler.brille.sats.SatsType
import no.nav.hjelpemidler.brille.vedtak.Behandlingsresultat
import no.nav.hjelpemidler.brille.vedtak.Vedtak
import no.nav.hjelpemidler.brille.vilkarsvurdering.Vilkårsvurdering
import org.junit.jupiter.api.Test
import java.time.LocalDate

class UtbetalingServiceTest {

    @Test
    internal fun `registrer ny utbetaling, send til utbetaling, og sett til utbetalt`() {
        runBlocking {
            val sessionContext = createDatabaseSessionContextWithMocks()
            val databaseContext = createDatabaseContext(sessionContext)
            val utbetalingService = UtbetalingService(databaseContext, mockk(relaxed = true))

            every {
                sessionContext.utbetalingStore.lagreUtbetaling(any())
            } answers {
                firstArg<Utbetaling>().copy(id = 1L)
            }

            every {
                sessionContext.utbetalingStore.oppdaterStatus(any())
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

            every {
                sessionContext.utbetalingStore.hentUtbetalingerMedBatchId(nyUtbetaling.batchId)
            } answers {
                listOf(nyUtbetaling)
            }

            every {
                sessionContext.utbetalingStore.lagreUtbetalingsBatch(any())
            } answers {
                1
            }

            every {
                sessionContext.tssIdentStore.hentTssIdent(any())
            } answers {
                "1234"
            }

            val batchList = utbetalingService.hentUtbetalingerMedBatchId(nyUtbetaling.batchId).toUtbetalingsBatchList()
            batchList.forEach {
                val tssIdent = transaction(databaseContext) { ctx -> ctx.tssIdentStore.hentTssIdent(it.orgNr) }!!
                utbetalingService.sendBatchTilUtbetaling(it, tssIdent)
            }
        }
    }
}
