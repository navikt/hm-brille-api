package no.nav.hjelpemidler.brille.vedtak

import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import no.nav.hjelpemidler.brille.nare.evaluering.Evalueringer
import no.nav.hjelpemidler.brille.sats.SatsType
import no.nav.hjelpemidler.brille.test.withMigratedDB
import no.nav.hjelpemidler.brille.vilkarsvurdering.Vilkårsvurdering
import no.nav.hjelpemidler.brille.virksomhet.Virksomhet
import no.nav.hjelpemidler.brille.virksomhet.VirksomhetStorePostgres
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.Test

internal class VedtakStorePostgresTest {
    @Test
    internal fun `lagrer og henter vedtak`() = withMigratedDB {
        val virksomhet = VirksomhetStorePostgres(it).lagreVirksomhet(
            Virksomhet(
                orgnr = "127627797",
                kontonr = "55718628082",
                epost = "test@test",
                fnrInnsender = "27121346260",
                navnInnsender = "",
                aktiv = true,
            )
        )

        val store = VedtakStorePostgres(it)
        val sats = SatsType.SATS_1
        val lagretVedtak = store.lagreVedtak(
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
        val vedtakForBarn = store.hentVedtakForBarn(lagretVedtak.fnrBarn).firstOrNull()
        vedtakForBarn.shouldNotBeNull()
        vedtakForBarn.fnrBarn shouldBe lagretVedtak.fnrBarn

        val orgnr = store.hentTidligereBrukteOrgnrForInnsender(lagretVedtak.fnrInnsender).firstOrNull()
        orgnr shouldBe virksomhet.orgnr
        orgnr shouldBe lagretVedtak.orgnr

        store.lagreVedtak(
            Vedtak(
                fnrBarn = "12121314156",
                fnrInnsender = "11080642360",
                orgnr = virksomhet.orgnr,
                bestillingsdato = LocalDate.now(),
                brillepris = sats.beløp.toBigDecimal(),
                bestillingsreferanse = "test 2",
                vilkårsvurdering = Vilkårsvurdering("test 2 ", Evalueringer().ja("test 2")),
                behandlingsresultat = Behandlingsresultat.INNVILGET,
                sats = sats,
                satsBeløp = sats.beløp,
                satsBeskrivelse = sats.beskrivelse,
                beløp = sats.beløp.toBigDecimal(),
            )
        )

        val vedtakList =
            store.hentVedtakIkkeRegistrertForUtbetaling<Vedtak<*>>(opprettet = LocalDateTime.now().minusDays(1))
        vedtakList.size shouldBeGreaterThan 1
    }
}
