package no.nav.hjelpemidler.brille.vilkarsvurdering

import no.nav.hjelpemidler.brille.jsonMapper
import no.nav.hjelpemidler.brille.medlemskap.MedlemskapResultat
import no.nav.hjelpemidler.brille.pdl.Foedsel
import no.nav.hjelpemidler.brille.pdl.PdlHentPerson
import no.nav.hjelpemidler.brille.pdl.PdlOppslag
import no.nav.hjelpemidler.brille.pdl.PdlPerson
import no.nav.hjelpemidler.brille.pdl.PdlPersonResponse
import no.nav.hjelpemidler.brille.sats.BeregnSats
import no.nav.hjelpemidler.brille.sats.Diopter
import java.time.LocalDate
import kotlin.test.Test

internal class Vilkår_v1Test {
    @Test
    internal fun `foo bar`() {
        val grunnlag = lagGrunnlag(false, 10)
        val evaluering = Vilkår_v1.Brille_v1.evaluer(grunnlag)
        println(jsonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(evaluering))
    }

    private fun lagGrunnlag(harFåttBrilleDetteKalenderåret: Boolean, alder: Int?) = Vilkår_v1.Grunnlag_v1(
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
        beregnSats = BeregnSats(
            høyreSfære = Diopter.ONE,
            høyreSylinder = Diopter.ZERO,
            venstreSfære = Diopter.ZERO,
            venstreSylinder = Diopter.ZERO
        ),
        bestillingsdato = LocalDate.now(),
        medlemskapResultat = MedlemskapResultat(
            kanSøke = true,
            medlemskapBevist = true,
            uavklartMedlemskap = false,
            emptyList()
        )
    )
}
