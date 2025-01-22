package no.nav.hjelpemidler.brille.utbetaling

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.test.runTest
import no.nav.hjelpemidler.brille.sats.SatsType
import no.nav.hjelpemidler.brille.test.AbstractStoreTest
import no.nav.hjelpemidler.brille.vedtak.Behandlingsresultat
import no.nav.hjelpemidler.brille.vedtak.KravKilde
import no.nav.hjelpemidler.brille.vedtak.Vedtak
import no.nav.hjelpemidler.brille.vedtak.toDto
import no.nav.hjelpemidler.brille.vilkarsvurdering.Vilkårsvurdering
import no.nav.hjelpemidler.brille.virksomhet.Virksomhet
import no.nav.hjelpemidler.nare.evaluering.Evalueringer
import org.junit.jupiter.api.Test
import org.postgresql.util.PSQLException
import java.time.LocalDate
import java.time.LocalDateTime

class UtbetalingStorePostgresTest : AbstractStoreTest() {
    @Test
    fun `lagrer og henter utbetaling`() = runTest {
        val virksomhet = transaction {
            virksomhetStore.lagreVirksomhet(
                Virksomhet(
                    orgnr = "127627798",
                    kontonr = "55718628082",
                    epost = "test@test",
                    fnrInnsender = "27121346260",
                    navnInnsender = "",
                    aktiv = true,
                ),
            )
        }

        val sats = SatsType.SATS_1
        transaction {
            val lagretVedtak1 = vedtakStore.lagreVedtak(
                Vedtak(
                    fnrBarn = "12121320922",
                    fnrInnsender = "11080642360",
                    navnInnsender = "Kronjuvel Sedat",
                    orgnr = virksomhet.orgnr,
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
                ),
            )

            val lagretVedtak2 = vedtakStore.lagreVedtak(
                Vedtak(
                    fnrBarn = "12121320923",
                    fnrInnsender = "11080642360",
                    navnInnsender = "Kronjuvel Sedat",
                    orgnr = virksomhet.orgnr,
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
                ),
            )

            val utbetaling1 = utbetalingStore.lagreUtbetaling(
                Utbetaling(
                    vedtakId = lagretVedtak1.id,
                    referanse = lagretVedtak1.bestillingsreferanse,
                    utbetalingsdato = lagretVedtak1.bestillingsdato,
                    vedtak = lagretVedtak1.toDto(),
                ),
            )
            val utbetaling2 = utbetalingStore.lagreUtbetaling(
                Utbetaling(
                    vedtakId = lagretVedtak2.id,
                    referanse = lagretVedtak2.bestillingsreferanse,
                    utbetalingsdato = lagretVedtak2.bestillingsdato,
                    vedtak = lagretVedtak2.toDto(),
                ),
            )
            utbetaling1.id shouldBeGreaterThan 0
            utbetalingStore
                .hentUtbetalingForVedtak(utbetaling1.vedtakId)
                .shouldNotBeNull {
                    this.id shouldBe utbetaling1.id
                    this.referanse shouldBe "test"
                    this.vedtak.orgnr shouldBe lagretVedtak1.orgnr
                    this.vedtak.beløp shouldBe lagretVedtak1.beløp
                    this.batchId shouldContain "127627798"
                    this.status shouldBe UtbetalingStatus.NY
                }

            utbetaling1.batchId shouldBe utbetaling2.batchId
            utbetaling1.batchDato shouldBe utbetaling2.batchDato
            val nyeUtbetalinger =
                utbetalingStore.hentUtbetalingerMedStatusBatchDatoOpprettet(batchDato = LocalDate.now())
            val batchUtbetalinger = utbetalingStore.hentUtbetalingerMedBatchId(utbetaling1.batchId)
            nyeUtbetalinger.size shouldBe 2
            batchUtbetalinger.size shouldBe nyeUtbetalinger.size
            val batchList = batchUtbetalinger.toUtbetalingBatchList()
            batchList.size shouldBe 1
            val batchRecord = batchList[0].toUtbetalingsbatch()
            utbetalingStore.lagreUtbetalingsbatch(batchRecord) shouldBe 1
            val hentDb = utbetalingStore.hentUtbetalingsbatch(batchRecord.batchId)
            hentDb.shouldNotBeNull()
            hentDb.antallUtbetalinger shouldBe batchRecord.antallUtbetalinger

            val tiMinutterSiden = LocalDateTime.now().minusMinutes(10)
            val utbetalinger10MinSiden = utbetalingStore.hentUtbetalingerMedStatusBatchDatoOpprettet(
                batchDato = LocalDate.now(),
                opprettet = tiMinutterSiden,
            )
            utbetalinger10MinSiden.size shouldBe 0

            val duplicateException = shouldThrow<PSQLException> {
                utbetalingStore.lagreUtbetalingsbatch(batchRecord)
            }
            duplicateException.message shouldContain "duplicate key"
        }
    }
}
