package no.nav.hjelpemidler.brille.sats

import java.time.LocalDate
import kotlin.math.abs

class SatsKalkulator(private val grunnlag: SatsGrunnlag) {
    constructor(brilleseddel: Brilleseddel, bestillingsdato: LocalDate = LocalDate.now()) : this(
        grunnlag = SatsGrunnlag(
            brilleseddel = brilleseddel,
            bestillingsdato = bestillingsdato,
        ),
    )

    fun kalkuler(): SatsType =
        beregnSats(grunnlag).sats
}

fun beregnSats(grunnlag: SatsGrunnlag): SatsBeregning {
    val brilleseddel = grunnlag.brilleseddel
    val sfære = maxOf(abs(brilleseddel.høyreSfære), abs(brilleseddel.venstreSfære))
    val sylinder = maxOf(brilleseddel.høyreSylinder, brilleseddel.venstreSylinder)
    val sats = when {
        sfære in SatsType.SATS_5.sfære || sylinder in SatsType.SATS_5.sylinder -> SatsType.SATS_5
        sfære in SatsType.SATS_4.sfære && sylinder in SatsType.SATS_4.sylinder -> SatsType.SATS_4
        sfære in SatsType.SATS_3.sfære || sylinder in SatsType.SATS_3.sylinder -> SatsType.SATS_3
        sfære in SatsType.SATS_2.sfære && sylinder in SatsType.SATS_2.sylinder -> SatsType.SATS_2
        sfære in SatsType.SATS_1.sfære || sylinder in SatsType.SATS_1.sylinder -> SatsType.SATS_1
        else -> SatsType.INGEN
    }
    return SatsBeregning(
        sats = sats,
        satsBeskrivelse = sats.beskrivelse,
        satsBeløp = sats.beløp(grunnlag.bestillingsdato)
    )
}
