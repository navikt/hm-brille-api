package no.nav.hjelpemidler.brille.sats

class SatsKalkulator(private val grunnlag: SatsGrunnlag) {
    fun kalkuler(): SatsType {
        val sfære = maxOf(grunnlag.høyreSfære, grunnlag.venstreSfære)
        val sylinder = maxOf(grunnlag.høyreSylinder, grunnlag.venstreSylinder)
        return when {
            sfære in SatsType.SATS_5.sfære || sylinder >= `06,25D` -> SatsType.SATS_5
            sfære in SatsType.SATS_4.sfære && sylinder <= `06,00D` -> SatsType.SATS_4
            sfære in SatsType.SATS_3.sfære || sylinder in `04,25D`..`06,00D` -> SatsType.SATS_3
            sfære in SatsType.SATS_2.sfære && sylinder <= `04,00D` -> SatsType.SATS_2
            sfære in SatsType.SATS_1.sfære || sylinder in `01,00D`..`04,00D` -> SatsType.SATS_1
            else -> SatsType.INGEN
        }
    }

    @Suppress("ObjectPropertyName")
    companion object {
        val `01,00D` = "01,00D".toDiopter()
        val `04,00D` = "04,00D".toDiopter()
        val `04,25D` = "04,25D".toDiopter()
        val `06,00D` = "06,00D".toDiopter()
        val `06,25D` = "06,25D".toDiopter()
    }
}
