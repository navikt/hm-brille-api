package no.nav.hjelpemidler.brille.sats

import java.math.BigDecimal

enum class SatsType(
    val sfære: ClosedRange<Diopter>,
    val sylinder: ClosedRange<Diopter>,
    val beskrivelse: String,
    val beløp: BigDecimal,
) {
    SATS_1(
        sfære = "1,00" til "4,00",
        sylinder = "1,00" til "4,00",
        beskrivelse = "Briller med sfærisk styrke på minst ett glass ≥ 1,00D ≤ 4,00D og/eller cylinderstyrke ≥ 1,00D ≤ 4,00",
        beløp = "900".toBigDecimal()
    ),
    SATS_2(
        sfære = "4,25" til "6,00",
        sylinder = tilOgMed("4,00"),
        beskrivelse = "Briller med sfærisk styrke på minst ett glass ≥ 4,25D ≤ 6,00D og cylinderstyrke ≤ 4,00",
        beløp = "1800".toBigDecimal()
    ),
    SATS_3(
        sfære = "6,25" til "8,00",
        sylinder = "4,25" til "6,00",
        beskrivelse = "Briller med sfærisk styrke på minst ett glass ≥ 6,25D ≤ 8,00D og/eller cylinderstyrke ≥ 4,25D ≤ 6,00",
        beløp = "2325".toBigDecimal()
    ),
    SATS_4(
        sfære = "8,25" til "10,00",
        sylinder = tilOgMed("6,00"),
        beskrivelse = "Briller med sfærisk styrke på minst ett glass ≥ 8,25D ≤ 10,00D og cylinderstyrke ≤ 6,00",
        beløp = "2700".toBigDecimal()
    ),
    SATS_5(
        sfære = "10,25".ellerMer(),
        sylinder = "6,25".ellerMer(),
        beskrivelse = "Briller med sfærisk styrke på minst ett glass ≥ 10,25D og/eller cylinderstyrke ≥ 6,25",
        beløp = "3975".toBigDecimal()
    ),
    INGEN(
        sfære = Diopter.INGEN,
        sylinder = Diopter.INGEN,
        beskrivelse = "INGEN",
        beløp = BigDecimal.ZERO
    );
}
