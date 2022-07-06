package no.nav.hjelpemidler.brille.vilkarsvurdering

import no.nav.hjelpemidler.brille.nare.evaluering.Evaluering
import no.nav.hjelpemidler.brille.nare.evaluering.Resultat

data class VilkårsvurderingResultat<T>(
    val grunnlag: T,
    val evaluering: Evaluering,
) {
    val utfall: Resultat get() = evaluering.resultat
}
