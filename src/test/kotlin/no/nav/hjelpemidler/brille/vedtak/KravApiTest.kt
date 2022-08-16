package no.nav.hjelpemidler.brille.vedtak

import io.kotest.matchers.shouldBe
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import no.nav.hjelpemidler.brille.medlemskap.MedlemskapBarn
import no.nav.hjelpemidler.brille.medlemskap.MedlemskapResultat
import no.nav.hjelpemidler.brille.pdl.PdlClient
import no.nav.hjelpemidler.brille.pdl.lagMockPdlOppslag
import no.nav.hjelpemidler.brille.sats.Brilleseddel
import no.nav.hjelpemidler.brille.test.TestRouting
import no.nav.hjelpemidler.brille.utbetaling.UtbetalingService
import no.nav.hjelpemidler.brille.vilkarsvurdering.DATO_ORDNINGEN_STARTET
import no.nav.hjelpemidler.brille.vilkarsvurdering.Vilkårsgrunnlag
import no.nav.hjelpemidler.brille.vilkarsvurdering.VilkårsgrunnlagDto
import no.nav.hjelpemidler.brille.vilkarsvurdering.VilkårsgrunnlagExtrasDto
import no.nav.hjelpemidler.brille.vilkarsvurdering.VilkårsvurderingService
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

internal class KravApiTest {

    private val vedtakStore = mockk<VedtakStore>()
    private val pdlClient = mockk<PdlClient>()
    private val medlemskapBarn = mockk<MedlemskapBarn>()
    private val dagensDatoFactory = mockk<() -> LocalDate>()
    private val utbetalingService = mockk<UtbetalingService>()

    private val vilkårsvurderingService = VilkårsvurderingService(
        vedtakStore,
        pdlClient,
        medlemskapBarn,
        dagensDatoFactory
    )

    private val vedtakService = VedtakService(vedtakStore, vilkårsvurderingService, mockk(relaxed = true), utbetalingService)

    private val routing = TestRouting {
        authenticate("test") {
            kravApi(vedtakService, mockk(relaxed = true))
        }
    }

    @Test
    internal fun `happy day`() = kjørTest(
        krav = KravDto(
            vilkårsgrunnlag, orgAdresse = "", orgNavn = "",
            bestillingsreferanse = "", brukersNavn = ""
        )

    )

    private val vilkårsgrunnlag = VilkårsgrunnlagDto(
        orgnr = "",
        fnrBarn = "07480966982",
        brilleseddel = Brilleseddel(
            høyreSfære = 2.00,
            høyreSylinder = 1.00,
            venstreSfære = 5.00,
            venstreSylinder = 5.00,
        ),
        bestillingsdato = DATO_ORDNINGEN_STARTET,
        brillepris = BigDecimal.valueOf(3000),
        extras = VilkårsgrunnlagExtrasDto("", "")
    )

    private fun kjørTest(
        krav: KravDto,
        vedtakForBruker: List<EksisterendeVedtak> = emptyList(),
        fødselsdato: String = "2014-08-15",
        medlemskapResultat: MedlemskapResultat = MedlemskapResultat(
            medlemskapBevist = true,
            uavklartMedlemskap = false,
            saksgrunnlag = emptyList()
        ),
        dagensDato: LocalDate = DATO_ORDNINGEN_STARTET,
    ) {
        every {
            dagensDatoFactory()
        } returns dagensDato

        every {
            vedtakStore.hentVedtakForBarn(krav.vilkårsgrunnlag.fnrBarn)
        } returns vedtakForBruker

        every {
            vedtakStore.lagreVedtak<Vilkårsgrunnlag>(any())
        } returnsArgument 0

        coEvery {
            pdlClient.hentPerson(krav.vilkårsgrunnlag.fnrBarn)
        } returns lagMockPdlOppslag(fødselsdato)

        every {
            medlemskapBarn.sjekkMedlemskapBarn(krav.vilkårsgrunnlag.fnrBarn, krav.vilkårsgrunnlag.bestillingsdato)
        } returns medlemskapResultat

        every {
            utbetalingService.isEnabled()
        } returns false

        routing.test {
            val response = client.post("/krav") {
                setBody(krav)
            }
            response.status shouldBe HttpStatusCode.OK
            val vedtak = response.body<VedtakDto>()
            vedtak.beløp shouldBe BigDecimal.valueOf(2650)
        }
    }
}
