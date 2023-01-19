package no.nav.hjelpemidler.brille.vilkarsvurdering

import no.nav.hjelpemidler.brille.nare.evaluering.Resultat
import no.nav.hjelpemidler.brille.sats.SatsType
import java.math.BigDecimal

data class VilkårsvurderingDto(
    val resultat: Resultat,
    val sats: SatsType,
    val satsBeskrivelse: String,
    val satsBeløp: Int,
    val beløp: BigDecimal,
    val kravFraFørFraInnsender: Boolean
)
