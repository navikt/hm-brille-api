package no.nav.hjelpemidler.brille.vedtak

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import no.nav.hjelpemidler.brille.db.PostgresTestHelper
import no.nav.hjelpemidler.brille.db.PostgresTestHelper.withMigratedDb
import no.nav.hjelpemidler.brille.nare.evaluering.Evalueringer
import no.nav.hjelpemidler.brille.sats.SatsType
import no.nav.hjelpemidler.brille.vilkarsvurdering.Vilkårsvurdering
import no.nav.hjelpemidler.brille.virksomhet.Virksomhet
import no.nav.hjelpemidler.brille.virksomhet.VirksomhetStorePostgres
import org.junit.jupiter.api.Test
import java.time.LocalDate

class VedtakSlettetStorePostgresTest {

    @Test
    internal fun `lagrer og sletter vedtak`() =

        withMigratedDb {
            var virksomhet: Virksomhet
            with(VirksomhetStorePostgres(PostgresTestHelper.sessionFactory)) {

                virksomhet = lagreVirksomhet(
                    Virksomhet(
                        orgnr = "000000009",
                        kontonr = "12345678901",
                        epost = "test@test2",
                        fnrInnsender = "27121346262",
                        navnInnsender = "",
                        aktiv = true,
                    )
                )
            }
            var vedtakId: Long
            with(VedtakStorePostgres(PostgresTestHelper.sessionFactory)) {
                val sats = SatsType.SATS_1
                val lagretVedtak = lagreVedtak(
                    Vedtak(
                        fnrBarn = "12121320922",
                        fnrInnsender = "27121346262",
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
                this.lagreVedtakIKø(lagretVedtak.id, lagretVedtak.opprettet)
                vedtakId = lagretVedtak.id
            }
            with(SlettVedtakStorePostgres(PostgresTestHelper.sessionFactory)) {
                hentVedtakSlettet(vedtakId).shouldBeNull()
                slettVedtak(vedtakId, "")
                hentVedtakSlettet(vedtakId).shouldNotBeNull()
            }
        }
}
