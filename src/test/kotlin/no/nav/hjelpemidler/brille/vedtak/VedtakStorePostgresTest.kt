package no.nav.hjelpemidler.brille.vedtak

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.comparables.shouldBeGreaterThanOrEqualTo
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import no.nav.hjelpemidler.brille.sats.SatsType
import no.nav.hjelpemidler.brille.test.AbstractStoreTest
import no.nav.hjelpemidler.brille.vilkarsvurdering.Vilkårsgrunnlag
import no.nav.hjelpemidler.brille.vilkarsvurdering.Vilkårsvurdering
import no.nav.hjelpemidler.brille.virksomhet.Virksomhet
import no.nav.hjelpemidler.nare.regel.Regelevaluering
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime

class VedtakStorePostgresTest : AbstractStoreTest() {
    @Test
    fun `lagrer og henter vedtak`() = runTest {
        val virksomhet = transaction {
            virksomhetStore.lagreVirksomhet(
                Virksomhet(
                    orgnr = "127627797",
                    kontonr = "55718628081",
                    epost = "test@test",
                    fnrInnsender = "27121346261",
                    navnInnsender = "",
                    aktiv = true,
                ),
            )
        }

        val sats = SatsType.SATS_1
        val lagretVedtak = transaction {
            val lagretVedtak = vedtakStore.lagreVedtak(
                Vedtak(
                    fnrBarn = "12121320922",
                    fnrInnsender = "11080642360",
                    navnInnsender = "Kronjuvel Sedat",
                    orgnr = virksomhet.orgnr,
                    butikkId = null,
                    bestillingsdato = LocalDate.now(),
                    brillepris = sats.beløp(LocalDate.now()).toBigDecimal(),
                    bestillingsreferanse = "test",
                    vilkårsvurdering = Vilkårsvurdering("test", Regelevaluering.ja("test")),
                    behandlingsresultat = Behandlingsresultat.INNVILGET,
                    sats = sats,
                    satsBeløp = sats.beløp(LocalDate.now()),
                    satsBeskrivelse = sats.beskrivelse,
                    beløp = sats.beløp(LocalDate.now()).toBigDecimal(),
                    kilde = KravKilde.KRAV_APP,
                    avsendersystemOrgNr = virksomhet.orgnr,
                ),
            )
            vedtakStore.lagreVedtakIKø(lagretVedtak.id, lagretVedtak.opprettet)
            lagretVedtak
        }

        transaction {
            val vedtakForBarn = vedtakStore.hentVedtakForBarn(lagretVedtak.fnrBarn).firstOrNull()
            vedtakForBarn.shouldNotBeNull()
            vedtakForBarn.fnrBarn shouldBe lagretVedtak.fnrBarn

            val orgnr = vedtakStore.hentTidligereBrukteOrgnrForInnsender(lagretVedtak.fnrInnsender).firstOrNull()
            orgnr shouldBe virksomhet.orgnr
            orgnr shouldBe lagretVedtak.orgnr
        }

        transaction {
            val lagretVedtak2 = vedtakStore.lagreVedtak(
                Vedtak(
                    fnrBarn = "12121314156",
                    fnrInnsender = "11080642360",
                    navnInnsender = "Kronjuvel Sedat",
                    orgnr = virksomhet.orgnr,
                    butikkId = "12345678910",
                    bestillingsdato = LocalDate.now(),
                    brillepris = sats.beløp(LocalDate.now()).toBigDecimal(),
                    bestillingsreferanse = "test 2",
                    vilkårsvurdering = Vilkårsvurdering("test 2 ", Regelevaluering.ja("test 2")),
                    behandlingsresultat = Behandlingsresultat.INNVILGET,
                    sats = sats,
                    satsBeløp = sats.beløp(LocalDate.now()),
                    satsBeskrivelse = sats.beskrivelse,
                    beløp = sats.beløp(LocalDate.now()).toBigDecimal(),
                    kilde = KravKilde.KRAV_APP,
                ),
            )

            vedtakStore.lagreVedtakIKø(lagretVedtak2.id, lagretVedtak2.opprettet)
            vedtakStore.hentAntallVedtakIKø() shouldBe 2

            // Test før tss-ident eksisterer
            vedtakStore.hentVedtakForUtbetaling<Vedtak<*>>(LocalDateTime.now()).shouldBeEmpty()

            // Test etter tss-ident eksisterer
            tssIdentStore.settTssIdent(virksomhet.orgnr, "12345678910")

            val vedtakForUtbetaling = vedtakStore.hentVedtakForUtbetaling<Vedtak<*>>(LocalDateTime.now())
            vedtakForUtbetaling.size shouldBeGreaterThan 1
            vedtakForUtbetaling.forEach { vedtakStore.fjernFraVedTakKø(it.id) }

            vedtakStore.hentVedtakForUtbetaling<Vedtak<*>>(LocalDateTime.now()).shouldBeEmpty()
            vedtakStore.hentVedtakForBarn("12121314156").size shouldBeGreaterThanOrEqualTo 1
            vedtakStore.hentVedtak<Vilkårsgrunnlag>(lagretVedtak2.id).shouldNotBeNull()
            vedtakStore.hentAntallVedtakIKø() shouldBe 0
        }
    }
}
