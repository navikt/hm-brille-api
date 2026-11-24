package no.nav.hjelpemidler.brille.vedtak

import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import no.nav.hjelpemidler.brille.db.MockDatabaseContext
import no.nav.hjelpemidler.brille.kafka.KafkaService
import no.nav.hjelpemidler.brille.medlemskap.MedlemskapResultat
import no.nav.hjelpemidler.brille.medlemskap.MedlemskapResultatResultat
import no.nav.hjelpemidler.brille.pdl.PdlOppslagPerson
import no.nav.hjelpemidler.brille.sats.Brilleseddel
import no.nav.hjelpemidler.brille.sats.SatsType
import no.nav.hjelpemidler.brille.vilkarsvurdering.Vilkårsgrunnlag
import no.nav.hjelpemidler.brille.vilkarsvurdering.VilkårsgrunnlagDto
import no.nav.hjelpemidler.brille.vilkarsvurdering.VilkårsgrunnlagExtrasDto
import no.nav.hjelpemidler.brille.vilkarsvurdering.Vilkårsvurdering
import no.nav.hjelpemidler.brille.vilkarsvurdering.VilkårsvurderingService
import no.nav.hjelpemidler.nare.regel.Regelevaluering
import no.nav.hjelpemidler.serialization.jackson.jsonMapper
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.math.BigDecimal
import java.time.LocalDate

class VedtakServiceTest {
    @ParameterizedTest
    @CsvSource(
        "123.454, 123.45, 123.45",
        "123.455, 123.46, 123.46",
        "799.99, 799.99, 799.99",
        "800.00, 800.00, 800.00",
        "800.01, 800.01, 800.00",
    )
    fun `lagrer vedtak med øreavrundet brillepris og utbetaling begrenset av sats`(
        prisInput: String,
        forventetBrillepris: String,
        forventetBeløp: String,
    ) = runTest {
        val databaseContext = MockDatabaseContext()
        val vilkårsvurderingService = mockk<VilkårsvurderingService>()
        val kafkaService = mockk<KafkaService>(relaxed = true)
        val service = VedtakService(databaseContext, vilkårsvurderingService, kafkaService)
        val dato = LocalDate.of(2024, 1, 1)
        val brilleseddel = Brilleseddel(
            høyreSfære = 1.0,
            høyreSylinder = 1.0,
            venstreSfære = 1.0,
            venstreSylinder = 1.0,
        )
        val vilkårsgrunnlag = Vilkårsgrunnlag(
            vedtakBarn = emptyList(),
            pdlOppslagBarn = PdlOppslagPerson(null, jsonMapper.nullNode()),
            medlemskapResultat = MedlemskapResultat(MedlemskapResultatResultat.JA, emptyList()),
            brilleseddel = brilleseddel,
            bestillingsdato = dato,
            mottaksdato = dato,
            dagensDato = dato,
        )
        val vilkårsvurdering = Vilkårsvurdering(vilkårsgrunnlag, Regelevaluering.ja("test fixture"))
        val krav = KravDto(
            vilkårsgrunnlag = VilkårsgrunnlagDto(
                orgnr = "123456789",
                fnrBarn = "00000000001",
                brilleseddel = brilleseddel,
                bestillingsdato = dato,
                brillepris = BigDecimal(prisInput),
                extras = VilkårsgrunnlagExtrasDto("Test Optiker", "test-referanse"),
            ),
            bestillingsreferanse = "test-referanse",
            orgAdresse = "Testgata 1",
            orgNavn = "Test Optiker",
        )
        val lagretVedtak = slot<Vedtak<Vilkårsgrunnlag>>()
        val køVedtakId = slot<Long>()

        coEvery {
            vilkårsvurderingService.vurderVilkår("00000000001", brilleseddel, dato)
        } returns vilkårsvurdering
        every {
            databaseContext.vedtakStore.lagreVedtak(capture(lagretVedtak))
        } answers {
            firstArg<Vedtak<Vilkårsgrunnlag>>().copy(id = 123L)
        }
        every {
            databaseContext.vedtakStore.lagreVedtakIKø(capture(køVedtakId), any())
        } returns 1L

        val result = service.lagVedtak(
            fnrInnsender = "00000000002",
            navnInnsender = "Test Innsender",
            navnBarn = "Test Barn",
            krav = krav,
            kilde = KravKilde.KRAV_APP,
        )

        val vedtakLagretIButikk = lagretVedtak.captured.copy(id = 123L)
        result shouldBe vedtakLagretIButikk
        lagretVedtak.captured.sats shouldBe SatsType.SATS_1
        lagretVedtak.captured.satsBeløp shouldBe 800
        lagretVedtak.captured.brillepris shouldBe BigDecimal(forventetBrillepris)
        lagretVedtak.captured.beløp shouldBe BigDecimal(forventetBeløp)
        køVedtakId.captured shouldBe result.id
        verify(exactly = 1) {
            databaseContext.vedtakStore.lagreVedtakIKø(result.id, any())
        }
        val vedtakSendtKafka = slot<Vedtak<Vilkårsgrunnlag>>()
        verify(exactly = 1) {
            kafkaService.vedtakFattet(krav, capture(vedtakSendtKafka), "Test Barn")
        }
        vedtakSendtKafka.captured shouldBe result
        (vedtakSendtKafka.captured === result) shouldBe true
    }
}
