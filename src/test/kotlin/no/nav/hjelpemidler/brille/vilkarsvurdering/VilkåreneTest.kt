package no.nav.hjelpemidler.brille.vilkarsvurdering

import no.nav.hjelpemidler.brille.jsonMapper
import no.nav.hjelpemidler.brille.medlemskap.MedlemskapResultat
import no.nav.hjelpemidler.brille.pdl.Foedsel
import no.nav.hjelpemidler.brille.pdl.PdlHentPerson
import no.nav.hjelpemidler.brille.pdl.PdlOppslag
import no.nav.hjelpemidler.brille.pdl.PdlPerson
import no.nav.hjelpemidler.brille.pdl.PdlPersonResponse
import no.nav.hjelpemidler.brille.sats.Brilleseddel
import no.nav.hjelpemidler.brille.sats.Diopter
import java.time.LocalDate
import kotlin.test.Test

internal class VilkåreneTest {
    @Test
    internal fun `foo bar`() {
        val grunnlag = lagGrunnlag(false, 10)
        val evaluering = Vilkårene.Brille.evaluer(grunnlag)
        println(jsonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(evaluering))
    }

    private fun lagGrunnlag(harFåttBrilleDetteKalenderåret: Boolean, alder: Int?) = Vilkårsgrunnlag(
        vedtakForBruker = emptyList(),
        pdlOppslagBruker = PdlOppslag(
            PdlPersonResponse(
                data = PdlHentPerson(
                    PdlPerson(
                        foedsel = listOf(Foedsel("2014", "2014-08-01"))
                    )
                )
            ),
            jsonMapper.nullNode()
        ),
        brilleseddel = Brilleseddel(
            høyreSfære = Diopter.ONE,
            høyreSylinder = Diopter.ZERO,
            venstreSfære = Diopter.ZERO,
            venstreSylinder = Diopter.ZERO
        ),
        bestillingsdato = LocalDate.now(),
        medlemskapResultat = MedlemskapResultat(
            medlemskapBevist = true,
            uavklartMedlemskap = false,
            emptyList()
        )
    )
}
