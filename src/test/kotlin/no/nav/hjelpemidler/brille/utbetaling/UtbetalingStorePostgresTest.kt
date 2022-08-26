package no.nav.hjelpemidler.brille.utbetaling

import io.kotest.matchers.date.shouldBeAfter
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import no.nav.hjelpemidler.brille.db.PostgresTestHelper
import no.nav.hjelpemidler.brille.db.PostgresTestHelper.withMigratedDb
import no.nav.hjelpemidler.brille.nare.evaluering.Evalueringer
import no.nav.hjelpemidler.brille.sats.SatsType
import no.nav.hjelpemidler.brille.vedtak.Behandlingsresultat
import no.nav.hjelpemidler.brille.vedtak.Vedtak
import no.nav.hjelpemidler.brille.vedtak.VedtakStorePostgres
import no.nav.hjelpemidler.brille.vedtak.toDto
import no.nav.hjelpemidler.brille.vilkarsvurdering.Vilkårsvurdering
import no.nav.hjelpemidler.brille.virksomhet.Virksomhet
import no.nav.hjelpemidler.brille.virksomhet.VirksomhetStorePostgres
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime

internal class UtbetalingStorePostgresTest {

    @Test
    internal fun `lagrer og henter utbetaling`() = withMigratedDb {

        runBlocking {
            with(UtbetalingStorePostgres(PostgresTestHelper.sessionFactory)) {
                with(VirksomhetStorePostgres(PostgresTestHelper.sessionFactory)) {
                    val virksomhet = lagreVirksomhet(
                        Virksomhet(
                            orgnr = "127627798",
                            kontonr = "55718628082",
                            epost = "test@test",
                            fnrInnsender = "27121346260",
                            navnInnsender = "",
                            aktiv = true,
                        )
                    )

                    val sats = SatsType.SATS_1
                    with(VedtakStorePostgres(PostgresTestHelper.sessionFactory)) {
                        val lagretVedtak = this.lagreVedtak(
                            Vedtak(
                                fnrBarn = "12121320922",
                                fnrInnsender = "11080642360",
                                orgnr = virksomhet.orgnr,
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
                        )

                        val utbetaling = lagreUtbetaling(
                            Utbetaling(
                                vedtakId = lagretVedtak.id,
                                referanse = lagretVedtak.bestillingsreferanse,
                                utbetalingsdato = lagretVedtak.bestillingsdato,
                                vedtak = lagretVedtak.toDto()
                            )
                        )
                        utbetaling.id shouldBeGreaterThan 0
                        val hentUtbetaling = hentUtbetalingForVedtak(utbetaling.vedtakId).shouldNotBeNull()
                        hentUtbetaling.id shouldBe utbetaling.id
                        hentUtbetaling.referanse shouldBe "test"
                        hentUtbetaling.vedtak.orgnr shouldBe lagretVedtak.orgnr
                        hentUtbetaling.vedtak.beløp shouldBe lagretVedtak.beløp
                        hentUtbetaling.status shouldBe UtbetalingStatus.NY
        val lagretVedtak2 = VedtakStorePostgres(it).lagreVedtak(
            Vedtak(
                fnrBarn = "12121320923",
                fnrInnsender = "11080642360",
                orgnr = virksomhet.orgnr,
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
        )

        val utbetaling = store.lagreUtbetaling(
            Utbetaling(
                vedtakId = lagretVedtak.id,
                referanse = lagretVedtak.bestillingsreferanse,
                utbetalingsdato = lagretVedtak.bestillingsdato,
                vedtak = lagretVedtak.toDto()
            )
        )

        val utbetaling2 = store.lagreUtbetaling(
            Utbetaling(
                vedtakId = lagretVedtak2.id,
                referanse = lagretVedtak2.bestillingsreferanse,
                utbetalingsdato = lagretVedtak2.bestillingsdato,
                vedtak = lagretVedtak2.toDto()
            )
        )

        utbetaling.id shouldBeGreaterThan 0
        val hentUtbetaling = store.hentUtbetalingForVedtak(utbetaling.vedtakId).shouldNotBeNull()
        hentUtbetaling.id shouldBe utbetaling.id
        hentUtbetaling.referanse shouldBe "test"
        hentUtbetaling.vedtak.orgnr shouldBe lagretVedtak.orgnr
        hentUtbetaling.vedtak.beløp shouldBe lagretVedtak.beløp
        hentUtbetaling.status shouldBe UtbetalingStatus.NY

                        val statusOppdatert = oppdaterStatus(
                            hentUtbetaling.copy(
                                status = UtbetalingStatus.TIL_UTBETALING,
                                oppdatert = LocalDateTime.now()
                            )
                        )
                        val tilUtbetaling = hentUtbetalingForVedtak(utbetaling.vedtakId).shouldNotBeNull()
                        tilUtbetaling.status shouldBe UtbetalingStatus.TIL_UTBETALING
                        statusOppdatert.referanse shouldBe tilUtbetaling.referanse
                        tilUtbetaling.opprettet shouldBe hentUtbetaling.opprettet
                        tilUtbetaling.oppdatert shouldBeAfter hentUtbetaling.oppdatert
                    }
                }
            }
        }
        val utbetalinger = store.hentUtbetalingerMedStatus()
        utbetalinger.size shouldBe 2

        val statusOppdatert = store.oppdaterStatus(
            hentUtbetaling.copy(
                status = UtbetalingStatus.TIL_UTBETALING,
                oppdatert = LocalDateTime.now()
            )
        )
        val utbetalinger2 = store.hentUtbetalingerMedStatus()
        utbetalinger2.size shouldBe 1

        val tilUtbetaling = store.hentUtbetalingForVedtak(utbetaling.vedtakId).shouldNotBeNull()
        tilUtbetaling.status shouldBe UtbetalingStatus.TIL_UTBETALING
        statusOppdatert.referanse shouldBe tilUtbetaling.referanse
        tilUtbetaling.opprettet shouldBe hentUtbetaling.opprettet
        tilUtbetaling.oppdatert shouldBeAfter hentUtbetaling.oppdatert
    }
}
