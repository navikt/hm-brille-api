package no.nav.hjelpemidler.brille.sats

class SatsKalkulator(private val brilleseddel: Brilleseddel) {
    fun kalkuler(): SatsType {
        val sfære = maxOf(brilleseddel.høyreSfære, brilleseddel.venstreSfære)
        val sylinder = maxOf(brilleseddel.høyreSylinder, brilleseddel.venstreSylinder)
        return when {
            sfære in SatsType.SATS_5.sfære || sylinder in SatsType.SATS_5.sylinder -> SatsType.SATS_5
            sfære in SatsType.SATS_4.sfære && sylinder in SatsType.SATS_4.sylinder -> SatsType.SATS_4
            sfære in SatsType.SATS_3.sfære || sylinder in SatsType.SATS_3.sylinder -> SatsType.SATS_3
            sfære in SatsType.SATS_2.sfære && sylinder in SatsType.SATS_2.sylinder -> SatsType.SATS_2
            sfære in SatsType.SATS_1.sfære || sylinder in SatsType.SATS_1.sylinder -> SatsType.SATS_1
            else -> SatsType.INGEN
        }
    }
}
