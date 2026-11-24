package no.nav.hjelpemidler.brille.vedtak

import io.kotest.matchers.shouldBe
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.hjelpemidler.brille.audit.AuditService
import no.nav.hjelpemidler.brille.db.MockDatabaseContext
import no.nav.hjelpemidler.brille.hotsak.HotsakClient
import no.nav.hjelpemidler.brille.medlemskap.MedlemskapBarn
import no.nav.hjelpemidler.brille.medlemskap.MedlemskapResultat
import no.nav.hjelpemidler.brille.medlemskap.MedlemskapResultatResultat
import no.nav.hjelpemidler.brille.pdl.PdlClient
import no.nav.hjelpemidler.brille.pdl.PdlService
import no.nav.hjelpemidler.brille.pdl.lagMockPdlOppslag
import no.nav.hjelpemidler.brille.redis.RedisClient
import no.nav.hjelpemidler.brille.sats.Brilleseddel
import no.nav.hjelpemidler.brille.sats.SatsType
import no.nav.hjelpemidler.brille.test.TestRouting
import no.nav.hjelpemidler.brille.utbetaling.Utbetaling
import no.nav.hjelpemidler.brille.utbetaling.UtbetalingService
import no.nav.hjelpemidler.brille.utbetaling.UtbetalingStatus
import no.nav.hjelpemidler.brille.vilkarsvurdering.DATO_ORDNINGEN_STARTET
import no.nav.hjelpemidler.brille.vilkarsvurdering.VilkårsgrunnlagDto
import no.nav.hjelpemidler.brille.vilkarsvurdering.VilkårsgrunnlagExtrasDto
import no.nav.hjelpemidler.brille.vilkarsvurdering.Vilkårsvurdering
import no.nav.hjelpemidler.brille.vilkarsvurdering.VilkårsvurderingService
import no.nav.hjelpemidler.nare.regel.Regelevaluering
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.math.BigDecimal
import java.time.LocalDate

class KravApiTest {
    private val pdlClient = mockk<PdlClient>()
    private val hotsakClient = mockk<HotsakClient>()
    private val medlemskapBarn = mockk<MedlemskapBarn>()
    private val dagensDatoFactory = mockk<() -> LocalDate>()
    private val auditService = mockk<AuditService>(relaxed = true)
    private val utbetalingService = mockk<UtbetalingService>(relaxed = true)
    private val slettVedtakService = mockk<SlettVedtakService>(relaxed = true)
    private val redisClient = mockk<RedisClient>(relaxed = true)
    private val pdlService = mockk<PdlService>(relaxed = true)

    private val databaseContext = MockDatabaseContext()

    private val vilkårsvurderingService = VilkårsvurderingService(
        databaseContext,
        pdlClient,
        hotsakClient,
        medlemskapBarn,
        dagensDatoFactory,
    )

    private val vedtakService =
        VedtakService(databaseContext, vilkårsvurderingService, mockk(relaxed = true))

    private val routing = TestRouting {
        authenticate("test") {
            kravApi(vedtakService, auditService, slettVedtakService, utbetalingService, redisClient, pdlService)
        }
    }

    @Test
    fun `happy day`() {
        runBlocking {
            kjørTest(
                krav = KravDto(
                    vilkårsgrunnlag,
                    orgAdresse = "",
                    orgNavn = "",
                    bestillingsreferanse = "",
                ),

            )
        }
    }

    @Test
    fun `sletting av ukjent krav gir ikke sideeffekter`() {
        every { databaseContext.vedtakStore.hentVedtak<Any>(42) } returns null

        routing.test {
            client.delete("/krav/42").status shouldBe HttpStatusCode.NotFound
        }

        coVerify(exactly = 0) { utbetalingService.hentUtbetalingForVedtak(any()) }
        coVerify(exactly = 0) { slettVedtakService.slettVedtak(any(), any(), any()) }
        coVerify(exactly = 0) { auditService.lagreOppslag(any(), any(), any()) }
    }

    @Test
    fun `innsender kan ikke slette andres krav`() {
        every { databaseContext.vedtakStore.hentVedtak<Any>(42) } returns mockedVedtak.copy(
            id = 42,
            fnrInnsender = "00000000002",
        )

        routing.test {
            client.delete("/krav/42").status shouldBe HttpStatusCode.Unauthorized
        }

        coVerify(exactly = 0) { utbetalingService.hentUtbetalingForVedtak(any()) }
        coVerify(exactly = 0) { slettVedtakService.slettVedtak(any(), any(), any()) }
        coVerify(exactly = 0) { auditService.lagreOppslag(any(), any(), any()) }
    }

    @ParameterizedTest
    @EnumSource(UtbetalingStatus::class)
    fun `krav med registrert utbetaling kan ikke slettes`(status: UtbetalingStatus) {
        val vedtak = mockedVedtak.copy(id = 42, fnrInnsender = routing.principal.fnr)
        every { databaseContext.vedtakStore.hentVedtak<Any>(vedtak.id) } returns vedtak
        coEvery { utbetalingService.hentUtbetalingForVedtak(vedtak.id) } returns Utbetaling(
            vedtakId = vedtak.id,
            vedtak = vedtak.toDto(),
            referanse = vedtak.bestillingsreferanse,
            utbetalingsdato = null,
            status = status,
        )

        routing.test {
            val response = client.delete("/krav/${vedtak.id}")
            response.status shouldBe HttpStatusCode.Unauthorized
            response.bodyAsText() shouldBe """{"error": "Krav kan ikke slettes fordi utbetaling er påstartet"}"""
        }

        coVerify(exactly = 0) { slettVedtakService.slettVedtak(any(), any(), any()) }
        coVerify(exactly = 0) { auditService.lagreOppslag(any(), any(), any()) }
    }

    @Test
    fun `innsender kan slette eget krav uten registrert utbetaling`() {
        val vedtak = mockedVedtak.copy(id = 42, fnrInnsender = routing.principal.fnr)
        every { databaseContext.vedtakStore.hentVedtak<Any>(vedtak.id) } returns vedtak
        coEvery { utbetalingService.hentUtbetalingForVedtak(vedtak.id) } returns null

        routing.test {
            client.delete("/krav/${vedtak.id}").status shouldBe HttpStatusCode.OK
        }

        coVerify(exactly = 1) {
            slettVedtakService.slettVedtak(vedtak.id, routing.principal.fnr, SlettetAvType.INNSENDER)
        }
        coVerify(exactly = 1) {
            auditService.lagreOppslag(
                routing.principal.fnr,
                vedtak.fnrBarn,
                "[DELETE] /krav - Sletting av krav ${vedtak.id}",
            )
        }
    }

    private val vilkårsgrunnlag = VilkårsgrunnlagDto(
        orgnr = "123456789",
        butikkId = null,
        fnrBarn = "07480966982",
        brilleseddel = Brilleseddel(
            høyreSfære = 2.0,
            høyreSylinder = 1.0,
            venstreSfære = 5.0,
            venstreSylinder = 5.0,
        ),
        bestillingsdato = DATO_ORDNINGEN_STARTET,
        brillepris = BigDecimal.valueOf(3000),
        extras = VilkårsgrunnlagExtrasDto("", ""),
    )

    val mockedVedtak = Vedtak<Any>(
        fnrBarn = "12121314156",
        fnrInnsender = "15084300133",
        navnInnsender = "Kronjuvel Sedat",
        orgnr = "123456789",
        butikkId = null,
        bestillingsdato = LocalDate.now(),
        brillepris = SatsType.SATS_1.beløp(LocalDate.now()).toBigDecimal(),
        bestillingsreferanse = "test 2",
        vilkårsvurdering = Vilkårsvurdering("test 2 ", Regelevaluering.ja("test 2")),
        behandlingsresultat = Behandlingsresultat.INNVILGET,
        sats = SatsType.SATS_1,
        satsBeløp = SatsType.SATS_1.beløp(LocalDate.now()),
        satsBeskrivelse = SatsType.SATS_1.beskrivelse,
        beløp = SatsType.SATS_1.beløp(LocalDate.now()).toBigDecimal(),
        kilde = KravKilde.KRAV_APP,
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
            databaseContext.vedtakStore.hentVedtakForBarn(krav.vilkårsgrunnlag.fnrBarn)
        } returns vedtakForBruker

        coEvery {
            hotsakClient.hentEksisterendeVedtak(any(), any())
        } returns emptyList()

        every {
            databaseContext.vedtakStore.hentVedtak<Any>(any())
        } returns mockedVedtak

        every {
            databaseContext.vedtakStore.lagreVedtak<Any>(any())
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
            vedtak.beløp shouldBe BigDecimal.valueOf(800)
            val delete = client.delete("/krav/${vedtak.id}")
            delete.status shouldBe HttpStatusCode.OK
        }
    }
}
