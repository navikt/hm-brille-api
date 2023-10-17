package no.nav.hjelpemidler.brille.sats

data class SatsBeregning(
    val sats: SatsType,
    val satsBeskrivelse: String,
    val satsBeløp: Int,
) {
    companion object {
        fun ingen() = SatsBeregning(
            sats = SatsType.INGEN,
            satsBeskrivelse = SatsType.INGEN.beskrivelse,
            satsBeløp = 0,
        )
    }
}
