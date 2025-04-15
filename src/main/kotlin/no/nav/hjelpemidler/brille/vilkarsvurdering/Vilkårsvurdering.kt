package no.nav.hjelpemidler.brille.vilkarsvurdering

import com.fasterxml.jackson.annotation.JsonIgnore
import no.nav.hjelpemidler.brille.Configuration
import no.nav.hjelpemidler.brille.writePrettyString
import no.nav.hjelpemidler.nare.regel.Regelevaluering
import no.nav.hjelpemidler.nare.regel.Regelutfall
import no.nav.hjelpemidler.serialization.jackson.jsonMapper

data class Vilkårsvurdering<T>(
    val grunnlag: T,
    val evaluering: Regelevaluering,
) {
    val utfall: Regelutfall get() = evaluering.resultat
    val programvareVersjon: String get() = Configuration.GIT_COMMIT

    @JsonIgnore
    fun toJson(): String = jsonMapper.writePrettyString(this)
}
