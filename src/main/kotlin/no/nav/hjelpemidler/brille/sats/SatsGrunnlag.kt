package no.nav.hjelpemidler.brille.sats

import no.nav.hjelpemidler.brille.vilkarsvurdering.VilkårsvurderingGrunnlag

data class SatsGrunnlag(
    val høyreSfære: Diopter,
    val høyreSylinder: Diopter,
    val venstreSfære: Diopter,
    val venstreSylinder: Diopter,
) : VilkårsvurderingGrunnlag
