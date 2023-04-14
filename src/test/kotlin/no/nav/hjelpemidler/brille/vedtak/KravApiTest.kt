package no.nav.hjelpemidler.brille.vedtak

import io.kotest.common.runBlocking
import io.kotest.matchers.shouldBe
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import no.nav.hjelpemidler.brille.audit.AuditService
import no.nav.hjelpemidler.brille.db.createDatabaseContext
import no.nav.hjelpemidler.brille.db.createDatabaseSessionContextWithMocks
import no.nav.hjelpemidler.brille.medlemskap.MedlemskapBarn
import no.nav.hjelpemidler.brille.medlemskap.MedlemskapResultat
import no.nav.hjelpemidler.brille.medlemskap.MedlemskapResultatResultat
import no.nav.hjelpemidler.brille.nare.evaluering.Evalueringer
import no.nav.hjelpemidler.brille.pdl.PdlClient
import no.nav.hjelpemidler.brille.pdl.lagMockPdlOppslag
import no.nav.hjelpemidler.brille.redis.RedisClient
import no.nav.hjelpemidler.brille.sats.Brilleseddel
import no.nav.hjelpemidler.brille.sats.SatsType
import no.nav.hjelpemidler.brille.test.TestRouting
import no.nav.hjelpemidler.brille.utbetaling.UtbetalingService
import no.nav.hjelpemidler.brille.vilkarsvurdering.DATO_ORDNINGEN_STARTET
import no.nav.hjelpemidler.brille.vilkarsvurdering.VilkårsgrunnlagDto
import no.nav.hjelpemidler.brille.vilkarsvurdering.VilkårsgrunnlagExtrasDto
import no.nav.hjelpemidler.brille.vilkarsvurdering.Vilkårsvurdering
import no.nav.hjelpemidler.brille.vilkarsvurdering.VilkårsvurderingService
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

internal class KravApiTest {

    private val pdlClient = mockk<PdlClient>()
    private val medlemskapBarn = mockk<MedlemskapBarn>()
    private val dagensDatoFactory = mockk<() -> LocalDate>()
    private val auditService = mockk<AuditService>(relaxed = true)
    private val utbetalingService = mockk<UtbetalingService>(relaxed = true)
    private val slettVedtakService = mockk<SlettVedtakService>(relaxed = true)
    private val redisClient = mockk<RedisClient>(relaxed = true)

    val sessionContext = createDatabaseSessionContextWithMocks()
    val databaseContext = createDatabaseContext(sessionContext)

    private val vilkårsvurderingService = VilkårsvurderingService(
        databaseContext,
        pdlClient,
        medlemskapBarn,
        dagensDatoFactory
    )

    private val vedtakService =
        VedtakService(databaseContext, vilkårsvurderingService, mockk(relaxed = true))

    private val routing = TestRouting {
        authenticate("test") {
            kravApi(vedtakService, auditService, slettVedtakService, utbetalingService, redisClient)
        }
    }

    @Test
    internal fun `happy day`() {
        runBlocking {
            kjørTest(
                krav = KravDto(
                    vilkårsgrunnlag, orgAdresse = "", orgNavn = "",
                    bestillingsreferanse = "", brukersNavn = ""
                )

            )
        }
    }

    private val vilkårsgrunnlag = VilkårsgrunnlagDto(
        orgnr = "123456789",
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

    val mockedVedtak = Vedtak<Any>(
        fnrBarn = "12121314156",
        fnrInnsender = "15084300133",
        navnInnsender = "Kronjuvel Sedat",
        orgnr = "123456789",
        bestillingsdato = LocalDate.now(),
        brillepris = SatsType.SATS_1.beløp.toBigDecimal(),
        bestillingsreferanse = "test 2",
        vilkårsvurdering = Vilkårsvurdering("test 2 ", Evalueringer().ja("test 2")),
        behandlingsresultat = Behandlingsresultat.INNVILGET,
        sats = SatsType.SATS_1,
        satsBeløp = SatsType.SATS_1.beløp,
        satsBeskrivelse = SatsType.SATS_1.beskrivelse,
        beløp = SatsType.SATS_1.beløp.toBigDecimal()
    )

    private fun kjørTest(
        krav: KravDto,
        vedtakForBruker: List<EksisterendeVedtak> = emptyList(),
        fødselsdato: String = "2014-08-15",
        medlemskapResultat: MedlemskapResultat = MedlemskapResultat(
            resultat = MedlemskapResultatResultat.JA,
            saksgrunnlag = emptyList(),
        ),
        dagensDato: LocalDate = DATO_ORDNINGEN_STARTET,
    ) {

        every {
            sessionContext.vedtakStore.hentVedtakForBarn(krav.vilkårsgrunnlag.fnrBarn)
        } returns vedtakForBruker

        every {
            sessionContext.vedtakStore.hentVedtak<Any>(any())
        } returns mockedVedtak

        every {
            sessionContext.vedtakStore.lagreVedtak<Any>(any())
        } answers {
            mockedVedtak
        }

        every {
            dagensDatoFactory()
        } returns dagensDato
        coEvery {
            pdlClient.hentPerson(krav.vilkårsgrunnlag.fnrBarn)
        } returns lagMockPdlOppslag(fødselsdato)

        coEvery {
            medlemskapBarn.sjekkMedlemskapBarn(krav.vilkårsgrunnlag.fnrBarn, krav.vilkårsgrunnlag.bestillingsdato)
        } returns medlemskapResultat

        every {
            runBlocking { utbetalingService.hentUtbetalingForVedtak(any()) }
        } returns null

        routing.test {
            val response = client.post("/krav") {
                setBody(krav)
            }
            response.status shouldBe HttpStatusCode.OK
            val vedtak = response.body<VedtakDto>()
            vedtak.beløp shouldBe BigDecimal.valueOf(750)
            val delete = client.delete("/krav/${vedtak.id}")
            delete.status shouldBe HttpStatusCode.OK
        }
    }
}
