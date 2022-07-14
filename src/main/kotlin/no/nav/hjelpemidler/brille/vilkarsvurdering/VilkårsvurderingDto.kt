package no.nav.hjelpemidler.brille.vilkarsvurdering

import no.nav.hjelpemidler.brille.nare.evaluering.Resultat
import no.nav.hjelpemidler.brille.sats.SatsType

data class VilkårsvurderingDto(
    val resultat: Resultat,
    val sats: SatsType,
    val satsBeskrivelse: String,
    val satsBeløp: String,
    val beløp: String
)
