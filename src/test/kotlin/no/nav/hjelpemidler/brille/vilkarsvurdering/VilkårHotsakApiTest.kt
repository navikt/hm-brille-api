package no.nav.hjelpemidler.brille.vilkarsvurdering

import io.kotest.assertions.assertSoftly
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
import no.nav.hjelpemidler.brille.pdl.PdlClient
import no.nav.hjelpemidler.brille.pdl.lagMockPdlOppslag
import no.nav.hjelpemidler.brille.sats.Brilleseddel
import no.nav.hjelpemidler.brille.test.TestRouting
import no.nav.hjelpemidler.brille.test.skalMangleDokumentasjon
import no.nav.hjelpemidler.brille.test.skalVærePositiv
import no.nav.hjelpemidler.brille.test.verifiser
import no.nav.hjelpemidler.brille.test.`år på`
import no.nav.hjelpemidler.brille.vedtak.EksisterendeVedtak
import no.nav.hjelpemidler.brille.vedtak.VedtakService
import no.nav.hjelpemidler.nare.evaluering.Evaluering
import no.nav.hjelpemidler.nare.evaluering.Resultat
import no.nav.hjelpemidler.nare.spesifikasjon.Spesifikasjon
import java.time.LocalDate
import kotlin.test.Test

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
        fødselsdato = 18 `år på` DATO_ORDNINGEN_STARTET,
        forventetResultat = Resultat.NEI,
    )

    @Test
    internal fun `barnet fyller 18 år dagen etter bestillingsdato`() = kjørTest(
        fødselsdato = (18 `år på` DATO_ORDNINGEN_STARTET).plusDays(1),
        forventetResultat = Resultat.JA,
    )

    @Test
    internal fun `barnet fyller 18 år dagen før bestillingsdato`() = kjørTest(
        fødselsdato = (18 `år på` DATO_ORDNINGEN_STARTET).minusDays(1),
        forventetResultat = Resultat.NEI,
    )

    @Test
    internal fun `barnet er bevist ikke medlem i folketrygden`() = kjørTest(
        medlemskapResultat = MedlemskapResultat(
            resultat = MedlemskapResultatResultat.NEI,
            saksgrunnlag = emptyList(),
        ),
        forventetResultat = Resultat.NEI,
    )

    @Test
    internal fun `barnets medlemskap i folketrygden er uavklart`() = kjørTest(
        medlemskapResultat = MedlemskapResultat(
            resultat = MedlemskapResultatResultat.UAVKLART,
            saksgrunnlag = emptyList(),
        ),
        forventetResultat = Resultat.JA,
    )

    @Test
    internal fun `brillestyrke under minstegrense`() = kjørTest(
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

    @Test
    fun `bestillingsdato mangler, under 18 år i dag`() = kjørTest(
        fødselsdato = 10 `år på` DATO_ORDNINGEN_STARTET,
        vilkårsgrunnlag = defaultVilkårsgrunnlag.copy(bestillingsdato = null),
        forventetResultat = Resultat.NEI,
    ) {
        verifiser(Vilkårene.HarIkkeVedtakIKalenderåret) { skalMangleDokumentasjon() }
        verifiser(Vilkårene.Under18ÅrPåBestillingsdato) { skalVærePositiv() }
        verifiser(Vilkårene.MedlemAvFolketrygden) { skalMangleDokumentasjon() }
        verifiser(Vilkårene.Brillestyrke) { skalVærePositiv() }
        verifiser(Vilkårene.Bestillingsdato) { skalMangleDokumentasjon() }
    }

    @Test
    fun `bestillingsdato mangler, over 18 i dag`() = kjørTest(
        fødselsdato = 18 `år på` DATO_ORDNINGEN_STARTET,
        vilkårsgrunnlag = defaultVilkårsgrunnlag.copy(bestillingsdato = null),
        forventetResultat = Resultat.NEI,
    ) {
        verifiser(Vilkårene.HarIkkeVedtakIKalenderåret) { skalMangleDokumentasjon() }
        verifiser(Vilkårene.Under18ÅrPåBestillingsdato) { skalMangleDokumentasjon() }
        verifiser(Vilkårene.MedlemAvFolketrygden) { skalMangleDokumentasjon() }
        verifiser(Vilkårene.Brillestyrke) { skalVærePositiv() }
        verifiser(Vilkårene.Bestillingsdato) { skalMangleDokumentasjon() }
    }

    @Test
    fun `brilleseddel mangler`() = kjørTest(
        vilkårsgrunnlag = defaultVilkårsgrunnlag.copy(brilleseddel = null),
        forventetResultat = Resultat.NEI,
    ) {
        verifiser(Vilkårene.Brillestyrke) { skalMangleDokumentasjon() }
    }

    private fun kjørTest(
        vilkårsgrunnlag: VilkårsgrunnlagAdDto = defaultVilkårsgrunnlag,
        vedtakForBruker: List<EksisterendeVedtak> = emptyList(),
        fødselsdato: LocalDate = LocalDate.parse("2014-08-15"),
        medlemskapResultat: MedlemskapResultat = MedlemskapResultat(
            resultat = MedlemskapResultatResultat.JA,
            saksgrunnlag = emptyList(),
        ),
        dagensDato: LocalDate = DATO_ORDNINGEN_STARTET,
        forventetResultat: Resultat,
        assertions: VilkårsvurderingHotsakDto.(VilkårsvurderingHotsakDto) -> Unit = {},
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
        } returns lagMockPdlOppslag(fødselsdato.toString())

        coEvery {
            medlemskapBarn.sjekkMedlemskapBarn(
                vilkårsgrunnlag.fnrBarn,
                vilkårsgrunnlag.bestillingsdato ?: MANGLENDE_DATO,
            )
        } returns medlemskapResultat

        routing.test {
            val response = client.post("/ad/vilkarsgrunnlag") {
                setBody(vilkårsgrunnlag)
            }

            response.status shouldBe HttpStatusCode.OK

            assertSoftly(response.body<VilkårsvurderingHotsakDto>()) {
                resultat shouldBe forventetResultat
                satsBeløp.shouldNotBeNull()

                assertions(it)
            }
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
            bestillingsreferanse = "bestillingsreferanse",
        )

    private fun defaulVilkårMedBrilleseddel(
        høyreSfære: Double = 0.0,
        høyreSylinder: Double = 0.0,
        venstreSfære: Double = 0.0,
        venstreSylinder: Double = 0.0,
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

    private fun <T> VilkårsvurderingHotsakDto.verifiser(
        spesifikasjon: Spesifikasjon<T>,
        matcher: Evaluering.() -> Unit,
    ) = evaluering.verifiser(spesifikasjon, matcher)
}
