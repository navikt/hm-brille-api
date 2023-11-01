package no.nav.hjelpemidler.brille.vilkarsvurdering

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.mockk.Called
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
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
import no.nav.hjelpemidler.brille.test.TestRouting
import no.nav.hjelpemidler.brille.test.skalMangleDokumentasjon
import no.nav.hjelpemidler.brille.test.skalVærePositiv
import no.nav.hjelpemidler.brille.test.verifiser
import no.nav.hjelpemidler.brille.test.`år på`
import no.nav.hjelpemidler.brille.tid.MANGLENDE_DATO
import no.nav.hjelpemidler.brille.tid.minus
import no.nav.hjelpemidler.brille.tid.toInstant
import no.nav.hjelpemidler.brille.tid.toLocalDate
import no.nav.hjelpemidler.brille.vedtak.EksisterendeVedtak
import no.nav.hjelpemidler.brille.vedtak.VedtakService
import no.nav.hjelpemidler.brille.vedtak.lagEksisterendeVedtak
import no.nav.hjelpemidler.brille.vedtak.toList
import no.nav.hjelpemidler.nare.evaluering.Evaluering
import no.nav.hjelpemidler.nare.evaluering.Resultat
import no.nav.hjelpemidler.nare.spesifikasjon.Spesifikasjon
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.time.Duration.Companion.days

class VilkårHotsakApiTest {
    private val pdlClient = mockk<PdlClient>()
    private val hotsakClient = mockk<HotsakClient>()
    private val medlemskapBarn = mockk<MedlemskapBarn>()
    private val dagensDatoFactory = mockk<() -> LocalDate>()
    private val kafkaService = mockk<KafkaService>()

    private val sessionContext = createDatabaseSessionContextWithMocks()
    private val databaseContext = createDatabaseContext(sessionContext)

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
    fun `happy case`() = kjørTest(forventetResultat = Resultat.JA)

    @Test
    fun `har vedtak i kalenderåret`() {
        val bestillingsdato = LocalDate.now().minusMonths(2)
        kjørTest(
            vilkårsgrunnlag = lagVilkårsgrunnlagHotsakDto {
                this.bestillingsdato = bestillingsdato
            },
            vedtakForBruker = lagEksisterendeVedtak {
                this.bestillingsdato = bestillingsdato
            }.toList(),
            forventetResultat = Resultat.NEI,
        )
    }

    @Test
    fun `har vedtak i kalenderåret fra hotsak`() {
        val bestillingsdato = LocalDate.now().minusMonths(2)
        kjørTest(
            vilkårsgrunnlag = lagVilkårsgrunnlagHotsakDto {
                this.bestillingsdato = bestillingsdato

                vedtak {
                    this.bestillingsdato = bestillingsdato
                }
            },
            forventetResultat = Resultat.NEI,
        )
    }

    @Test
    fun `har vedtak i annet år`() {
        val bestillingsdato = LocalDate.now().minusMonths(2)
        kjørTest(
            vilkårsgrunnlag = lagVilkårsgrunnlagHotsakDto {
                this.bestillingsdato = bestillingsdato
            },
            vedtakForBruker = lagEksisterendeVedtak {
                this.bestillingsdato = bestillingsdato.minusYears(1)
            }.toList(),
            forventetResultat = Resultat.JA,
        )
    }

    @Test
    fun `barnet fyller 18 år på bestillingsdato`() {
        val bestillingsdato = LocalDate.now().minusMonths(2)
        kjørTest(
            lagVilkårsgrunnlagHotsakDto {
                this.bestillingsdato = bestillingsdato
            },
            fødselsdato = 18 `år på` bestillingsdato,
            forventetResultat = Resultat.NEI,
        )
    }

    @Test
    fun `barnet fyller 18 år dagen etter bestillingsdato`() {
        val bestillingsdato = LocalDate.now().minusMonths(2)
        kjørTest(
            lagVilkårsgrunnlagHotsakDto {
                this.bestillingsdato = bestillingsdato
            },
            fødselsdato = (18 `år på` bestillingsdato).plusDays(1),
            forventetResultat = Resultat.JA,
        )
    }

    @Test
    fun `barnet fyller 18 år dagen før bestillingsdato`() {
        val bestillingsdato = LocalDate.now().minusMonths(2)
        kjørTest(
            lagVilkårsgrunnlagHotsakDto {
                this.bestillingsdato = bestillingsdato
            },
            fødselsdato = (18 `år på` bestillingsdato).minusDays(1),
            forventetResultat = Resultat.NEI,
        )
    }

    @Test
    fun `barnet er bevist ikke medlem i folketrygden`() = kjørTest(
        medlemskapResultat = MedlemskapResultat(
            resultat = MedlemskapResultatResultat.NEI,
            saksgrunnlag = emptyList(),
        ),
        forventetResultat = Resultat.NEI,
    )

    @Test
    fun `barnets medlemskap i folketrygden er uavklart`() = kjørTest(
        medlemskapResultat = MedlemskapResultat(
            resultat = MedlemskapResultatResultat.UAVKLART,
            saksgrunnlag = emptyList(),
        ),
        forventetResultat = Resultat.JA,
    )

    @Test
    fun `brillestyrke under minstegrense`() = kjørTest(
        vilkårsgrunnlag = lagVilkårsgrunnlagHotsakDto {
            brilleseddel {
                høyreSfære = 0.0
                høyreSylinder = 0.0
                venstreSfære = 0.0
                venstreSylinder = 0.0
            }
        },
        forventetResultat = Resultat.NEI,
    )

    @Test
    fun `brillestyrke høyreSylinder over minstegrense`() = kjørTest(
        vilkårsgrunnlag = lagVilkårsgrunnlagHotsakDto {
            brilleseddel {
                høyreSylinder = 1.0
            }
        },
        forventetResultat = Resultat.JA,
    )

    @Test
    fun `brillestyrke venstreSfære over minstegrense`() = kjørTest(
        vilkårsgrunnlag = lagVilkårsgrunnlagHotsakDto {
            brilleseddel {
                venstreSfære = 1.0
            }
        },
        forventetResultat = Resultat.JA,
    )

    @Test
    fun `brillestyrke venstreSylinder over minstegrense`() = kjørTest(
        vilkårsgrunnlag = lagVilkårsgrunnlagHotsakDto {
            brilleseddel {
                venstreSylinder = 1.0
            }
        },
        forventetResultat = Resultat.JA,
    )

    @Test
    fun `bestillingsdato i fremtiden`() = kjørTest(
        vilkårsgrunnlag = lagVilkårsgrunnlagHotsakDto {
            bestillingsdato = LocalDate.now().plusDays(1)
        },
        forventetResultat = Resultat.NEI,
    )

    @Test
    fun `bestillingsdato mer enn 6 måneder tilbake i tid`() = kjørTest(
        vilkårsgrunnlag = lagVilkårsgrunnlagHotsakDto {
            bestillingsdato = LocalDate.now().minusMonths(6).minusDays(1)
            mottaksdato = Instant.now()
        },
        dagensDato = LocalDate.now(),
        forventetResultat = Resultat.NEI,
    )

    @Test
    fun `bestillingsdato mangler, ingen vedtak fra før, under 18 år i dag`() {
        val dagensDato = LocalDate.now()
        kjørTest(
            vilkårsgrunnlag = lagVilkårsgrunnlagHotsakDto {
                bestillingsdato = null
            },
            fødselsdato = (18 `år på` dagensDato).plusDays(1),
            dagensDato = dagensDato,
            forventetResultat = Resultat.NEI,
        ) {
            verifiser(Vilkårene.HarIkkeVedtakIKalenderåret) { skalVærePositiv() }
            verifiser(Vilkårene.Under18ÅrPåBestillingsdato) { skalVærePositiv() }
            verifiser(Vilkårene.MedlemAvFolketrygden) { skalMangleDokumentasjon() }
            verifiser(Vilkårene.Brillestyrke) { skalVærePositiv() }
            verifiser(Vilkårene.Bestillingsdato) { skalMangleDokumentasjon() }
        }
    }

    @Test
    fun `bestillingsdato mangler, ingen vedtak fra før, under 18 år på mottaksdato`() {
        val mottaksdato = Instant.now() - 10.days
        kjørTest(
            vilkårsgrunnlag = lagVilkårsgrunnlagHotsakDto {
                this.bestillingsdato = null
                this.mottaksdato = mottaksdato
            },
            fødselsdato = (18 `år på` mottaksdato.toLocalDate()).plusDays(1),
            forventetResultat = Resultat.NEI,
        ) {
            verifiser(Vilkårene.HarIkkeVedtakIKalenderåret) { skalVærePositiv() }
            verifiser(Vilkårene.Under18ÅrPåBestillingsdato) { skalVærePositiv() }
            verifiser(Vilkårene.MedlemAvFolketrygden) { skalMangleDokumentasjon() }
            verifiser(Vilkårene.Brillestyrke) { skalVærePositiv() }
            verifiser(Vilkårene.Bestillingsdato) { skalMangleDokumentasjon() }
        }
    }

    @Test
    fun `bestillingsdato mangler, ingen vedtak fra før, over 18 i dag`() {
        val dagensDato = LocalDate.now()
        kjørTest(
            vilkårsgrunnlag = lagVilkårsgrunnlagHotsakDto {
                bestillingsdato = null
                mottaksdato = dagensDato.minusDays(1).toInstant()
            },
            fødselsdato = 18 `år på` dagensDato,
            dagensDato = dagensDato,
            forventetResultat = Resultat.NEI,
        ) {
            verifiser(Vilkårene.HarIkkeVedtakIKalenderåret) { skalVærePositiv() }
            verifiser(Vilkårene.Under18ÅrPåBestillingsdato) { skalVærePositiv() }
            verifiser(Vilkårene.MedlemAvFolketrygden) { skalMangleDokumentasjon() }
            verifiser(Vilkårene.Brillestyrke) { skalVærePositiv() }
            verifiser(Vilkårene.Bestillingsdato) { skalMangleDokumentasjon() }
        }
    }

    @Test
    fun `brilleseddel mangler`() = kjørTest(
        vilkårsgrunnlag = lagVilkårsgrunnlagHotsakDto {
            brilleseddel = null
        },
        forventetResultat = Resultat.NEI,
    ) {
        verifiser(Vilkårene.Brillestyrke) { skalMangleDokumentasjon() }
    }

    private fun kjørTest(
        vilkårsgrunnlag: VilkårsgrunnlagHotsakDto = lagVilkårsgrunnlagHotsakDto {
            mottaksdato
        },
        vedtakForBruker: List<EksisterendeVedtak> = emptyList(),
        fødselsdato: LocalDate = 14 `år på` (vilkårsgrunnlag.bestillingsdato ?: LocalDate.now()),
        dagensDato: LocalDate = LocalDate.now(),
        medlemskapResultat: MedlemskapResultat = MedlemskapResultat(
            resultat = MedlemskapResultatResultat.JA,
            saksgrunnlag = emptyList(),
        ),
        forventetResultat: Resultat,
        assertions: VilkårsvurderingHotsakDto.(VilkårsvurderingHotsakDto) -> Unit = {},
    ) {
        every { dagensDatoFactory() } returns dagensDato

        every { sessionContext.vedtakStore.hentVedtakForBarn(vilkårsgrunnlag.fnrBarn) } returns vedtakForBruker

        coEvery { pdlClient.hentPerson(vilkårsgrunnlag.fnrBarn) } returns lagMockPdlOppslag(fødselsdato.toString())

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

        verify { hotsakClient wasNot Called }
    }

    private fun <T> VilkårsvurderingHotsakDto.verifiser(
        spesifikasjon: Spesifikasjon<T>,
        matcher: Evaluering.() -> Unit,
    ) = evaluering.verifiser(spesifikasjon, matcher)
}
