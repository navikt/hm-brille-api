package no.nav.hjelpemidler.brille.vilkarsvurdering

import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
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
import no.nav.hjelpemidler.brille.hotsak.HotsakVedtak
import no.nav.hjelpemidler.brille.hotsak.lagHotsakVedtak
import no.nav.hjelpemidler.brille.hotsak.toList
import no.nav.hjelpemidler.brille.medlemskap.MedlemskapBarn
import no.nav.hjelpemidler.brille.medlemskap.MedlemskapResultat
import no.nav.hjelpemidler.brille.medlemskap.MedlemskapResultatResultat
import no.nav.hjelpemidler.brille.pdl.PdlClient
import no.nav.hjelpemidler.brille.pdl.lagMockPdlOppslag
import no.nav.hjelpemidler.brille.sats.SatsType
import no.nav.hjelpemidler.brille.test.TestRouting
import no.nav.hjelpemidler.brille.test.`år på`
import no.nav.hjelpemidler.brille.vedtak.EksisterendeVedtak
import no.nav.hjelpemidler.brille.vedtak.lagEksisterendeVedtak
import no.nav.hjelpemidler.brille.vedtak.toList
import no.nav.hjelpemidler.nare.evaluering.Resultat
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

class VilkårApiTest {
    private val pdlClient = mockk<PdlClient>()
    private val hotsakClient = mockk<HotsakClient>()
    private val medlemskapBarn = mockk<MedlemskapBarn>()
    private val dagensDatoFactory = mockk<() -> LocalDate>()

    private val sessionContext = createDatabaseSessionContextWithMocks()
    private val databaseContext = createDatabaseContext(sessionContext)

    private val vilkårsvurderingService = VilkårsvurderingService(
        databaseContext,
        pdlClient,
        hotsakClient,
        medlemskapBarn,
        dagensDatoFactory,
    )

    private val routing = TestRouting {
        authenticate("test") {
            vilkårApi(vilkårsvurderingService, mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true))
        }
    }

    @Test
    fun `happy case`() = kjørTest(forventetResultat = Resultat.JA)

    @Test
    fun `har vedtak i kalenderåret`() {
        val bestillingsdato = LocalDate.now().minusMonths(2)
        kjørTest(
            vilkårsgrunnlag = lagVilkårsgrunnlagDto {
                this.bestillingsdato = bestillingsdato
            },
            vedtak = lagEksisterendeVedtak {
                this.bestillingsdato = bestillingsdato
            }.toList(),
            forventetResultat = Resultat.NEI,
        )
    }

    @Test
    fun `har vedtak i kalenderåret fra hotsak`() {
        val bestillingsdato = LocalDate.now().minusMonths(2)
        kjørTest(
            vilkårsgrunnlag = lagVilkårsgrunnlagDto {
                this.bestillingsdato = bestillingsdato
            },
            vedtakHotsak = lagHotsakVedtak {
                this.bestillingsdato = bestillingsdato
            }.toList(),
            forventetResultat = Resultat.NEI,
        )
    }

    @Test
    fun `har vedtak i kalenderåret samme innsender`() {
        val bestillingsdato = LocalDate.now().minusMonths(2)
        kjørTest(
            vilkårsgrunnlag = lagVilkårsgrunnlagDto {
                this.bestillingsdato = bestillingsdato
            },
            vedtak = lagEksisterendeVedtak {
                this.bestillingsdato = bestillingsdato
                this.fnrInnsender = "15084300133"
            }.toList(),
            forventetResultat = Resultat.NEI,
        )
    }

    @Test
    fun `har vedtak i annet år`() {
        val bestillingsdato = LocalDate.now().minusMonths(2)
        kjørTest(
            vilkårsgrunnlag = lagVilkårsgrunnlagDto {
                this.bestillingsdato = bestillingsdato
            },
            vedtak = lagEksisterendeVedtak {
                this.bestillingsdato = bestillingsdato.minusYears(1)
            }.toList(),
            forventetResultat = Resultat.JA,
        )
    }

    @Test
    fun `barnet fyller 18 år på bestillingsdato`() {
        val bestillingsdato = LocalDate.now().minusMonths(2)
        kjørTest(
            vilkårsgrunnlag = lagVilkårsgrunnlagDto {
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
            vilkårsgrunnlag = lagVilkårsgrunnlagDto {
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
            vilkårsgrunnlag = lagVilkårsgrunnlagDto {
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
        vilkårsgrunnlag = lagVilkårsgrunnlagDto {
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
        vilkårsgrunnlag = lagVilkårsgrunnlagDto {
            brilleseddel {
                høyreSylinder = 2.0
            }
        },
        forventetResultat = Resultat.JA,
    )

    @Test
    fun `brillestyrke venstreSfære over minstegrense`() = kjørTest(
        vilkårsgrunnlag = lagVilkårsgrunnlagDto {
            brilleseddel {
                venstreSfære = 3.0
            }
        },
        forventetResultat = Resultat.JA,
    )

    @Test
    fun `brillestyrke venstreSylinder over minstegrense`() = kjørTest(
        vilkårsgrunnlag = lagVilkårsgrunnlagDto {
            brilleseddel {
                venstreSylinder = 1.0
            }
        },
        forventetResultat = Resultat.JA,
    )

    @Test
    fun `bestillingsdato i fremtiden`() = kjørTest(
        vilkårsgrunnlag = lagVilkårsgrunnlagDto {
            bestillingsdato = LocalDate.now()
        },
        dagensDato = LocalDate.now().minusDays(1),
        forventetResultat = Resultat.NEI,
    )

    @Test
    fun `bestillingsdato mer enn 6 måneder tilbake i tid`() = kjørTest(
        vilkårsgrunnlag = lagVilkårsgrunnlagDto {
            bestillingsdato = LocalDate.now().minusMonths(6).minusDays(1)
        },
        forventetResultat = Resultat.NEI,
    )

    private fun kjørTest(
        vilkårsgrunnlag: VilkårsgrunnlagDto = lagVilkårsgrunnlagDto(),
        vedtak: List<EksisterendeVedtak> = emptyList(),
        vedtakHotsak: List<HotsakVedtak> = emptyList(),
        fødselsdato: LocalDate = 12 `år på` vilkårsgrunnlag.bestillingsdato,
        dagensDato: LocalDate = LocalDate.now(),
        medlemskapResultat: MedlemskapResultat = MedlemskapResultat(
            resultat = MedlemskapResultatResultat.JA,
            saksgrunnlag = emptyList(),
        ),
        forventetResultat: Resultat,
    ) {
        every { dagensDatoFactory() } returns dagensDato

        coEvery { hotsakClient.hentEksisterendeVedtak(any(), any()) } returns vedtakHotsak

        every { sessionContext.vedtakStore.hentVedtakForBarn(vilkårsgrunnlag.fnrBarn) } returns vedtak

        coEvery { pdlClient.hentPerson(vilkårsgrunnlag.fnrBarn) } returns lagMockPdlOppslag(fødselsdato.toString())

        coEvery {
            medlemskapBarn.sjekkMedlemskapBarn(
                vilkårsgrunnlag.fnrBarn,
                vilkårsgrunnlag.bestillingsdato,
            )
        } returns medlemskapResultat

        routing.test {
            val response = client.post("/vilkarsgrunnlag") { setBody(vilkårsgrunnlag) }
            response.status shouldBe HttpStatusCode.OK

            val vilkårsvurdering = response.body<VilkårsvurderingDto>()
            vilkårsvurdering.resultat shouldBe forventetResultat

            when (vilkårsvurdering.resultat) {
                Resultat.NEI -> vilkårsvurdering.sats shouldBe SatsType.INGEN

                else -> {
                    vilkårsvurdering.sats shouldNotBe SatsType.INGEN
                    vilkårsvurdering.beløp shouldNotBe BigDecimal.ZERO
                }
            }

            vilkårsvurdering.satsBeløp.shouldNotBeNull()
        }
    }
}
