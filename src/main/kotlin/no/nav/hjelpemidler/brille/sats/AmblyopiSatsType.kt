package no.nav.hjelpemidler.brille.sats

enum class AmblyopiSatsType(
    val sats: Int,
    val sfære: ClosedRange<Double>,
    val sylinder: ClosedRange<Double>,
    val add: ClosedRange<Double>,
    val beskrivelse: String,
    val beløp: Int,
) {
    SATS_1(
        sats = 1,
        sfære = 0.00..3.75,
        sylinder = Diopter.MIN..4.00,
        add = 0.00..0.75,
        beskrivelse = "Briller med sfærisk styrke på minst ett glass ≥ 0,00D ≤ 3,75D",
        beløp = 1230,
    ),
    SATS_2(
        sats = 2,
        sfære = 4.00..6.00,
        sylinder = Diopter.MIN..4.00,
        add = 0.00..0.75,
        beskrivelse = "Briller med sfærisk styrke på minst ett glass ≥ 4,00D ≤ 6,00D",
        beløp = 2460,
    ),
    INDIVIDUELT(
        sats = 0,
        sfære = 6.25..Diopter.MAX,
        sylinder = -4.0..Diopter.MIN_SYLINDER,
        add = 1.00..Diopter.MAX,
        beskrivelse = "Briller  med sfærisk styrke på minst ett glass ≥ 6,00D og cylinderstyrke ≥ 4,00D og add ≥ 1.00D",
        beløp = 0,
    ),
    INGEN(
        sats = 0,
        sfære = 0.0..0.0,
        sylinder = 0.0..0.0,
        add = 0.00..0.00,
        beskrivelse = "INGEN",
        beløp = 0,
    ),
}
