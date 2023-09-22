package no.nav.hjelpemidler.brille.sats

data class SatsBeregningAmblyopi(
    val sats: AmblyopiSatsType,
    val satsBeskrivelse: String,
    val satsBeløp: Int,
) {
    companion object {
        fun ingen() = SatsBeregningAmblyopi(
            sats = AmblyopiSatsType.INGEN,
            satsBeskrivelse = AmblyopiSatsType.INGEN.beskrivelse,
            satsBeløp = 0,
        )
    }
}
