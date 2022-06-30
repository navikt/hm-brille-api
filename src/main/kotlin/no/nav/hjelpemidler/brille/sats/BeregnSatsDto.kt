package no.nav.hjelpemidler.brille.sats

data class BeregnSatsDto(
    val høyreSfære: Diopter,
    val høyreSylinder: Diopter,
    val venstreSfære: Diopter,
    val venstreSylinder: Diopter,
)
