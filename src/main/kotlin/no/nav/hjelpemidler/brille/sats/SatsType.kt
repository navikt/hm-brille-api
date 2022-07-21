package no.nav.hjelpemidler.brille.sats

object Diopter {
    const val MIN: Double = -99.00
    const val MAX: Double = +99.00
}

fun ClosedRange<Double>.visning(): String = toString()

enum class SatsType(
    val sfære: ClosedRange<Double>,
    val sylinder: ClosedRange<Double>,
    val beskrivelse: String,
    val beløp: Int,
) {
    SATS_1(
        sfære = 1.00..4.00,
        sylinder = 1.00..4.00,
        beskrivelse = "Briller med sfærisk styrke på minst ett glass ≥ 1,00D ≤ 4,00D og/eller cylinderstyrke ≥ 1,00D ≤ 4,00D",
        beløp = 750
    ),
    SATS_2(
        sfære = 4.25..6.00,
        sylinder = Diopter.MIN..4.00,
        beskrivelse = "Briller med sfærisk styrke på minst ett glass ≥ 4,25D ≤ 6,00D og cylinderstyrke ≤ 4,00D",
        beløp = 1950
    ),
    SATS_3(
        sfære = 6.25..8.00,
        sylinder = 4.25..6.00,
        beskrivelse = "Briller med sfærisk styrke på minst ett glass ≥ 6,25D ≤ 8,00D og/eller cylinderstyrke ≥ 4,25D ≤ 6,00D",
        beløp = 2650
    ),
    SATS_4(
        sfære = 8.25..10.00,
        sylinder = Diopter.MIN..6.00,
        beskrivelse = "Briller med sfærisk styrke på minst ett glass ≥ 8,25D ≤ 10,00D og cylinderstyrke ≤ 6,00D",
        beløp = 3150
    ),
    SATS_5(
        sfære = 10.25..Diopter.MAX,
        sylinder = 6.25..Diopter.MAX,
        beskrivelse = "Briller med sfærisk styrke på minst ett glass ≥ 10,25D og/eller cylinderstyrke ≥ 6,25D",
        beløp = 4850
    ),
    INGEN(
        sfære = 0.0..0.0,
        sylinder = 0.0..0.0,
        beskrivelse = "INGEN",
        beløp = 0
    );
}
