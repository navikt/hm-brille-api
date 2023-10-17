package no.nav.hjelpemidler.brille.vilkarsvurdering

import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import no.nav.hjelpemidler.brille.db.createDatabaseContext
import no.nav.hjelpemidler.brille.db.createDatabaseSessionContextWithMocks
import no.nav.hjelpemidler.brille.hotsak.HotsakClient
import no.nav.hjelpemidler.brille.joarkref.JoarkrefService
import no.nav.hjelpemidler.brille.kafka.KafkaService
import no.nav.hjelpemidler.brille.medlemskap.MedlemskapBarn
import no.nav.hjelpemidler.brille.medlemskap.MedlemskapResultat
import no.nav.hjelpemidler.brille.medlemskap.MedlemskapResultatResultat
import no.nav.hjelpemidler.brille.nare.evaluering.Resultat
import no.nav.hjelpemidler.brille.pdl.PdlClient
import no.nav.hjelpemidler.brille.pdl.lagMockPdlOppslag
import no.nav.hjelpemidler.brille.sats.Brilleseddel
import no.nav.hjelpemidler.brille.test.TestRouting
import no.nav.hjelpemidler.brille.vedtak.EksisterendeVedtak
import no.nav.hjelpemidler.brille.vedtak.VedtakService
import org.junit.jupiter.api.Test
import java.time.LocalDate

internal class VilkårHotsakApiTest {
    private val pdlClient = mockk<PdlClient>()
    private val hotsakClient = mockk<HotsakClient>()
    private val medlemskapBarn = mockk<MedlemskapBarn>()
    private val dagensDatoFactory = mockk<() -> LocalDate>()
    private val kafkaService = mockk<KafkaService>()

    val sessionContext = createDatabaseSessionContextWithMocks()
    val databaseContext = createDatabaseContext(sessionContext)

    private val vilkårsvurderingService = VilkårsvurderingService(
        databaseContext,
        pdlClient,
        hotsakClient,
        medlemskapBarn,
        dagensDatoFactory,
    )

    private val vedtakService = VedtakService(
        databaseContext,
        vilkårsvurderingService,
        kafkaService,
    )

    private val joarkrefService = JoarkrefService(
        databaseContext,
    )

    private val routing = TestRouting {
        authenticate("test_azuread") {
            vilkårHotsakApi(vilkårsvurderingService, vedtakService, joarkrefService)
        }
    }

    @Test
    internal fun `happy case`() = kjørTest(forventetResultat = Resultat.JA)

    @Test
    internal fun `har vedtak i kalenderåret`() = kjørTest(
        vedtakForBruker = listOf(lagEksisterendeVedtak(DATO_ORDNINGEN_STARTET)),
        forventetResultat = Resultat.NEI,
    )

    @Test
    internal fun `har vedtak i annet år`() = kjørTest(
        vedtakForBruker = listOf(lagEksisterendeVedtak(DATO_ORDNINGEN_STARTET.minusYears(1))),
        forventetResultat = Resultat.JA,
    )

    @Test
    internal fun `barnet fyller 18 år på bestillingsdato`() = kjørTest(
        fødselsdato = DATO_ORDNINGEN_STARTET.minusYears(18).toString(),
        forventetResultat = Resultat.NEI,
    )

    @Test
    internal fun `barnet fyller 18 år dagen etter bestillingsdato`() = kjørTest(
        fødselsdato = DATO_ORDNINGEN_STARTET.minusYears(18).plusDays(1).toString(),
        forventetResultat = Resultat.JA,
    )

    @Test
    internal fun `barnet fyller 18 år dagen før bestillingsdato`() = kjørTest(
        fødselsdato = DATO_ORDNINGEN_STARTET.minusYears(18).minusDays(1).toString(),
        forventetResultat = Resultat.NEI,
    )

    @Test
    internal fun `barnet er bevist ikke medlem i folktrygden`() = kjørTest(
        medlemskapResultat = MedlemskapResultat(
            resultat = MedlemskapResultatResultat.NEI,
            saksgrunnlag = emptyList(),
        ),
        forventetResultat = Resultat.NEI,
    )

    @Test
    internal fun `barnets medlemskap i folktrygden er uavklart`() = kjørTest(
        medlemskapResultat = MedlemskapResultat(
            resultat = MedlemskapResultatResultat.UAVKLART,
            saksgrunnlag = emptyList(),
        ),
        forventetResultat = Resultat.JA,
    )

    @Test
    internal fun `brillestyrke under minstgrense`() = kjørTest(
        vilkårsgrunnlag = defaulVilkårMedBrilleseddel(),
        forventetResultat = Resultat.NEI,
    )

    @Test
    internal fun `brillestyrke høyreSylinder over minstegrense`() = kjørTest(
        vilkårsgrunnlag = defaulVilkårMedBrilleseddel(
            høyreSylinder = 2.00,
        ),
        forventetResultat = Resultat.JA,
    )

    @Test
    internal fun `brillestyrke venstreSfære over minstegrense`() = kjørTest(
        vilkårsgrunnlag = defaulVilkårMedBrilleseddel(
            venstreSfære = 3.00,
        ),
        forventetResultat = Resultat.JA,
    )

    @Test
    internal fun `brillestyrke venstreSylinder over minstegrense`() = kjørTest(
        vilkårsgrunnlag = defaulVilkårMedBrilleseddel(
            venstreSylinder = 1.00,
        ),
        forventetResultat = Resultat.JA,
    )

    @Test
    internal fun `bestillingsdato i fremtiden`() = kjørTest(
        vilkårsgrunnlag = defaultVilkårsgrunnlag.copy(bestillingsdato = DATO_ORDNINGEN_STARTET.plusDays(1)),
        forventetResultat = Resultat.NEI,
    )

    @Test
    internal fun `bestillingsdato mer enn 6 måneder tilbake i tid`() = kjørTest(
        vilkårsgrunnlag = defaultVilkårsgrunnlag.copy(bestillingsdato = DATO_ORDNINGEN_STARTET.plusMonths(1)),
        dagensDato = DATO_ORDNINGEN_STARTET.plusMonths(8),
        forventetResultat = Resultat.NEI,
    )

    private fun kjørTest(
        vilkårsgrunnlag: VilkårsgrunnlagAdDto = defaultVilkårsgrunnlag,
        vedtakForBruker: List<EksisterendeVedtak> = emptyList(),
        fødselsdato: String = "2014-08-15",
        medlemskapResultat: MedlemskapResultat = MedlemskapResultat(
            resultat = MedlemskapResultatResultat.JA,
            saksgrunnlag = emptyList(),
        ),
        dagensDato: LocalDate = DATO_ORDNINGEN_STARTET,
        forventetResultat: Resultat,
    ) {
        every {
            dagensDatoFactory()
        } returns dagensDato

        coEvery {
            hotsakClient.hentEksisterendeVedtakDato(any(), any())
        } returns null

        every {
            sessionContext.vedtakStore.hentVedtakForBarn(vilkårsgrunnlag.fnrBarn)
        } returns vedtakForBruker

        coEvery {
            pdlClient.hentPerson(vilkårsgrunnlag.fnrBarn)
        } returns lagMockPdlOppslag(fødselsdato)

        coEvery {
            medlemskapBarn.sjekkMedlemskapBarn(vilkårsgrunnlag.fnrBarn, vilkårsgrunnlag.bestillingsdato)
        } returns medlemskapResultat

        routing.test {
            val response = client.post("/ad/vilkarsgrunnlag") {
                setBody(vilkårsgrunnlag)
            }

            response.status shouldBe HttpStatusCode.OK
            val vilkårsvurdering = response.body<VilkårsvurderingHotsakDto>()
            vilkårsvurdering.resultat shouldBe forventetResultat

            vilkårsvurdering.satsBeløp.shouldNotBeNull()
        }
    }

    private fun lagEksisterendeVedtak(bestillingsdato: LocalDate) =
        EksisterendeVedtak(
            id = 1,
            fnrBarn = "12345678910",
            bestillingsdato = bestillingsdato,
            behandlingsresultat = "",
            opprettet = bestillingsdato.atStartOfDay(),
            fnrInnsender = "23456789101",
            bestillingsreferanse = "adawd",
        )

    private fun defaulVilkårMedBrilleseddel(
        høyreSfære: Double = 0.00,
        høyreSylinder: Double = 0.00,
        venstreSfære: Double = 0.00,
        venstreSylinder: Double = 0.00,
    ) =
        defaultVilkårsgrunnlag.copy(
            brilleseddel = Brilleseddel(
                høyreSfære = høyreSfære,
                høyreSylinder = høyreSylinder,
                venstreSfære = venstreSfære,
                venstreSylinder = venstreSylinder,
            ),
        )

    private val defaultVilkårsgrunnlag = VilkårsgrunnlagAdDto(
        fnrBarn = "07480966982",
        brilleseddel = Brilleseddel(
            høyreSfære = 1.00,
            høyreSylinder = 0.00,
            venstreSfære = 0.00,
            venstreSylinder = 0.00,
        ),
        bestillingsdato = DATO_ORDNINGEN_STARTET,
        brillepris = "1500".toBigDecimal(),
    )
}
