package no.nav.hjelpemidler.brille.vilkarsvurdering

import no.nav.hjelpemidler.brille.sats.SatsType
import no.nav.hjelpemidler.nare.regel.Regelevaluering
import no.nav.hjelpemidler.nare.regel.Regelutfall
import tools.jackson.databind.JsonNode
import java.math.BigDecimal

data class VilkårsvurderingHotsakDto(
    val resultat: Regelutfall,
    val sats: SatsType,
    val satsBeskrivelse: String,
    val satsBeløp: Int,
    val beløp: BigDecimal,
    val vilkårsgrunnlag: JsonNode,
    val evaluering: Regelevaluering,
)
