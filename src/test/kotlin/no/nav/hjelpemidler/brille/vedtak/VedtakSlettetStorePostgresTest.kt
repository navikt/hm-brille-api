package no.nav.hjelpemidler.brille.vedtak

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import kotlinx.coroutines.test.runTest
import no.nav.hjelpemidler.brille.sats.SatsType
import no.nav.hjelpemidler.brille.test.AbstractStoreTest
import no.nav.hjelpemidler.brille.vilkarsvurdering.Vilkårsvurdering
import no.nav.hjelpemidler.brille.virksomhet.Virksomhet
import no.nav.hjelpemidler.nare.regel.Regelevaluering
import org.junit.jupiter.api.Test
import java.time.LocalDate

class VedtakSlettetStorePostgresTest : AbstractStoreTest() {
    @Test
    fun `lagrer og sletter vedtak`() = runTest {
        val virksomhet = transaction {
            virksomhetStore.lagreVirksomhet(
                Virksomhet(
                    orgnr = "000000009",
                    kontonr = "12345678901",
                    epost = "test@test2",
                    fnrInnsender = "27121346262",
                    navnInnsender = "",
                    aktiv = true,
                ),
            )
        }
        val vedtakId = transaction {
            val sats = SatsType.SATS_1
            val lagretVedtak = vedtakStore.lagreVedtak(
                Vedtak(
                    fnrBarn = "12121320922",
                    fnrInnsender = "27121346262",
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
                ),
            )
            vedtakStore.lagreVedtakIKø(lagretVedtak.id, lagretVedtak.opprettet)
            lagretVedtak.id
        }
        transaction {
            slettVedtakStore.hentVedtakSlettet(vedtakId).shouldBeNull()
            slettVedtakStore.slettVedtak(vedtakId, "", SlettetAvType.INNSENDER)
            slettVedtakStore.hentVedtakSlettet(vedtakId).shouldNotBeNull()
        }
    }
}
