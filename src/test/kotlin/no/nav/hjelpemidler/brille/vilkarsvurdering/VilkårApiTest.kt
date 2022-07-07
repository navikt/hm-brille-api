package no.nav.hjelpemidler.brille.vilkarsvurdering

import com.fasterxml.jackson.module.kotlin.readValue
import io.kotest.matchers.shouldBe
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import no.nav.hjelpemidler.brille.jsonMapper
import no.nav.hjelpemidler.brille.medlemskap.MedlemskapBarn
import no.nav.hjelpemidler.brille.medlemskap.MedlemskapResultat
import no.nav.hjelpemidler.brille.nare.evaluering.Resultat
import no.nav.hjelpemidler.brille.pdl.PdlClient
import no.nav.hjelpemidler.brille.pdl.PdlOppslag
import no.nav.hjelpemidler.brille.pdl.PdlPersonResponse
import no.nav.hjelpemidler.brille.sats.BrilleseddelDto
import no.nav.hjelpemidler.brille.sats.tilDiopter
import no.nav.hjelpemidler.brille.test.TestRouting
import no.nav.hjelpemidler.brille.vedtak.VedtakStore
import java.time.LocalDate
import kotlin.test.Test

internal class VilkårApiTest {
    private val vedtakStore = mockk<VedtakStore>()
    private val pdlClient = mockk<PdlClient>()
    private val medlemskapBarn = mockk<MedlemskapBarn>()
    private val dagensDatoFactory = mockk<() -> LocalDate>()

    private val vilkårsvurderingService = VilkårsvurderingService(
        vedtakStore,
        pdlClient,
        medlemskapBarn,
        dagensDatoFactory,
    )

    private val routing = TestRouting {
        authenticate("test") {
            vilkårApi(vilkårsvurderingService, mockk(relaxed = true))
        }
    }

    @Test
    internal fun `foo bar`() {
        val vilkårsgrunnlag = VilkårsgrunnlagDto(
            orgnr = "",
            fnrBruker = "07480966982",
            brilleseddel = BrilleseddelDto(
                høyreSfære = "1".tilDiopter(),
                høyreSylinder = "0".tilDiopter(),
                venstreSfære = "0".tilDiopter(),
                venstreSylinder = "0".tilDiopter(),
            ),
            bestillingsdato = DATO_ORDNINGEN_STARTET,
            brillepris = "1500".toBigDecimal()
        )

        every {
            dagensDatoFactory()
        } returns DATO_ORDNINGEN_STARTET

        every {
            vedtakStore.hentVedtakForBruker(vilkårsgrunnlag.fnrBruker)
        } returns emptyList()

        coEvery {
            pdlClient.hentPerson(vilkårsgrunnlag.fnrBruker)
        } returns lagPdlOppslag()

        every {
            medlemskapBarn.sjekkMedlemskapBarn(vilkårsgrunnlag.fnrBruker, vilkårsgrunnlag.bestillingsdato)
        } returns MedlemskapResultat(medlemskapBevist = true, uavklartMedlemskap = false, saksgrunnlag = emptyList())

        routing.test {
            val response = client.post("/vilkarsgrunnlag") {
                setBody(vilkårsgrunnlag)
            }

            response.status shouldBe HttpStatusCode.OK
            response.body<VilkårsvurderingDto>().resultat shouldBe Resultat.JA
        }
    }

    private fun lagPdlOppslag(): PdlOppslag {
        val pdlPersonResponse = javaClass.getResourceAsStream("/mock/pdl.json").use {
            jsonMapper.readValue<PdlPersonResponse>(requireNotNull(it))
        }
        return PdlOppslag(pdlPersonResponse, jsonMapper.nullNode())
    }
}
