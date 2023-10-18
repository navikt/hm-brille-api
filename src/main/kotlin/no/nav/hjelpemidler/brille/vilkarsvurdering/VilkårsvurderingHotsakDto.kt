package no.nav.hjelpemidler.brille.vilkarsvurdering

import com.fasterxml.jackson.databind.JsonNode
import no.nav.hjelpemidler.brille.sats.SatsType
import no.nav.hjelpemidler.nare.evaluering.Evaluering
import no.nav.hjelpemidler.nare.evaluering.Resultat
import java.math.BigDecimal

data class VilkårsvurderingHotsakDto(
    val resultat: Resultat,
    val sats: SatsType,
    val satsBeskrivelse: String,
    val satsBeløp: Int,
    val beløp: BigDecimal,
    val vilkårsgrunnlag: JsonNode,
    val evaluering: Evaluering,
)
