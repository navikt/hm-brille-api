package no.nav.hjelpemidler.brille.vilkarsvurdering

import no.nav.hjelpemidler.brille.sats.SatsType
import no.nav.hjelpemidler.nare.regel.Regelutfall
import java.math.BigDecimal

data class VilkårsvurderingDto(
    val resultat: Regelutfall,
    val sats: SatsType,
    val satsBeskrivelse: String,
    val satsBeløp: Int,
    val beløp: BigDecimal,
    val kravFraFørFraInnsender: String?,
)
