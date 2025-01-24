package no.nav.hjelpemidler.brille.sats

import no.nav.hjelpemidler.brille.Configuration
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.NumberFormat
import java.time.LocalDate
import java.time.Month

object Diopter {
    const val MIN: Double = 0.0
    const val MAX: Double = 99.99

    const val MIN_SFÆRE: Double = -12.0
    const val MIN_SYLINDER: Double = -99.0

    private val format: NumberFormat = DecimalFormat("#0.00", DecimalFormatSymbols(Configuration.LOCALE))

    fun formater(diopter: Double): String = format.format(diopter)
}

fun ClosedRange<Double>.visning(): String = "≥ ${Diopter.formater(start)}D ≤ ${Diopter.formater(endInclusive)}D"

enum class SatsType(
    val sats: Int,
    val sfære: ClosedRange<Double>,
    val sylinder: ClosedRange<Double>,
    val beskrivelse: String,
) {
    SATS_1(
        sats = 1,
        sfære = 1.00..4.00,
        sylinder = 1.00..4.00,
        beskrivelse = "Briller med sfærisk styrke på minst ett glass ≥ 1,00D ≤ 4,00D og/eller cylinderstyrke ≥ 1,00D ≤ 4,00D",
    ),
    SATS_2(
        sats = 2,
        sfære = 4.25..6.00,
        sylinder = Diopter.MIN..4.00,
        beskrivelse = "Briller med sfærisk styrke på minst ett glass ≥ 4,25D ≤ 6,00D og cylinderstyrke ≤ 4,00D",
    ),
    SATS_3(
        sats = 3,
        sfære = 6.25..8.00,
        sylinder = 4.25..6.00,
        beskrivelse = "Briller med sfærisk styrke på minst ett glass ≥ 6,25D ≤ 8,00D og/eller cylinderstyrke ≥ 4,25D ≤ 6,00D",
    ),
    SATS_4(
        sats = 4,
        sfære = 8.25..10.00,
        sylinder = Diopter.MIN..6.00,
        beskrivelse = "Briller med sfærisk styrke på minst ett glass ≥ 8,25D ≤ 10,00D og cylinderstyrke ≤ 6,00D",
    ),
    SATS_5(
        sats = 5,
        sfære = 10.25..Diopter.MAX,
        sylinder = 6.25..Diopter.MAX,
        beskrivelse = "Briller med sfærisk styrke på minst ett glass ≥ 10,25D og/eller cylinderstyrke ≥ 6,25D",
    ),
    INGEN(
        sats = 0,
        sfære = 0.0..0.0,
        sylinder = 0.0..0.0,
        beskrivelse = "INGEN",
    ),
    ;

    private val datoForNyeSatser = LocalDate.of(2024, Month.JANUARY, 1)
    fun beløp(bestillingsdato: LocalDate): Int = when (bestillingsdato.isBefore(datoForNyeSatser)) {
        // Gamle satser
        true -> when (this) {
            SATS_1 -> 770
            SATS_2 -> 2000
            SATS_3 -> 2715
            SATS_4 -> 3230
            SATS_5 -> 4975
            INGEN -> 0
        }

        // Nye satser
        false -> when (this) {
            SATS_1 -> 800
            SATS_2 -> 2075
            SATS_3 -> 2820
            SATS_4 -> 3355
            SATS_5 -> 5165
            INGEN -> 0
        }
    }
}
