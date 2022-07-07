package no.nav.hjelpemidler.brille.vilkarsvurdering

import com.fasterxml.jackson.annotation.JsonIgnore
import no.nav.hjelpemidler.brille.jsonMapper
import no.nav.hjelpemidler.brille.nare.evaluering.Evaluering
import no.nav.hjelpemidler.brille.nare.evaluering.Resultat
import no.nav.hjelpemidler.brille.writePrettyString

data class Vilkårsvurdering<T>(
    val grunnlag: T,
    val evaluering: Evaluering,
) {
    val utfall: Resultat get() = evaluering.resultat

    @JsonIgnore
    fun toJson(): String = jsonMapper.writePrettyString(this)
}
