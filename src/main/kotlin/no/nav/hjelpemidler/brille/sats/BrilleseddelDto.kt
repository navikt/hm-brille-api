package no.nav.hjelpemidler.brille.sats

data class BrilleseddelDto(
    val høyreSfære: Diopter,
    val høyreSylinder: Diopter,
    val venstreSfære: Diopter,
    val venstreSylinder: Diopter,
) {
    fun tilBrilleseddel(): Brilleseddel = Brilleseddel(
        høyreSfære = this.høyreSfære,
        høyreSylinder = this.høyreSylinder,
        venstreSfære = this.venstreSfære,
        venstreSylinder = this.venstreSylinder,
    )
}
