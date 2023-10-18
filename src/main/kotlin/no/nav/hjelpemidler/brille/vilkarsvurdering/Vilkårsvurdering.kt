package no.nav.hjelpemidler.brille.vilkarsvurdering

import com.fasterxml.jackson.annotation.JsonIgnore
import no.nav.hjelpemidler.brille.Configuration
import no.nav.hjelpemidler.brille.jsonMapper
import no.nav.hjelpemidler.brille.writePrettyString
import no.nav.hjelpemidler.nare.evaluering.Evaluering
import no.nav.hjelpemidler.nare.evaluering.Resultat

data class Vilkårsvurdering<T>(
    val grunnlag: T,
    val evaluering: Evaluering,
) {
    val utfall: Resultat get() = evaluering.resultat
    val programvareVersjon: String get() = Configuration.gitCommit

    @JsonIgnore
    fun toJson(): String = jsonMapper.writePrettyString(this)
}
