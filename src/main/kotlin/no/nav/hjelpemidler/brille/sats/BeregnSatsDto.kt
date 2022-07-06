package no.nav.hjelpemidler.brille.sats

data class BeregnSatsDto(
    val høyreSfære: Diopter,
    val høyreSylinder: Diopter,
    val venstreSfære: Diopter,
    val venstreSylinder: Diopter,
) {
    fun tilBeregnSats(): BeregnSats = BeregnSats(
        høyreSfære = this.høyreSfære,
        høyreSylinder = this.høyreSylinder,
        venstreSfære = this.venstreSfære,
        venstreSylinder = this.venstreSylinder,
    )
}
