package no.nav.hjelpemidler.brille.vilkarsvurdering

import no.nav.hjelpemidler.brille.jsonMapper
import no.nav.hjelpemidler.brille.pdl.PersonDetaljerDto
import no.nav.hjelpemidler.brille.sats.Diopter
import no.nav.hjelpemidler.brille.sats.SatsGrunnlag
import kotlin.test.Test

internal class Vilkår_v1Test {
    @Test
    internal fun `foo bar`() {
        val grunnlag = lagGrunnlag(false, 10)
        val evaluering = Vilkår_v1.Brille_V1.evaluer(grunnlag)
        println(jsonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(evaluering))
    }

    private fun lagGrunnlag(harFåttBrilleDetteKalenderåret: Boolean, alder: Int?) = Vilkår_v1.Grunnlag_v1(
        harFåttBrilleDetteKalenderåret = harFåttBrilleDetteKalenderåret,
        personInformasjon = PersonDetaljerDto(
            fnr = "",
            fornavn = "",
            etternavn = "",
            adresse = "",
            postnummer = "",
            poststed = "",
            alder = alder,
            kommunenummer = ""
        ),
        satsGrunnlag = SatsGrunnlag(
            høyreSfære = Diopter.ONE,
            høyreSylinder = Diopter.ZERO,
            venstreSfære = Diopter.ZERO,
            venstreSylinder = Diopter.ZERO
        )
    )
}
