package no.nav.hjelpemidler.brille.sats

enum class SatsType(val sfære: ClosedRange<Diopter>, val beskrivelse: String, val beløp: String) {
    SATS_1(
        sfære = "1,00D".toDiopter().."4,00D".toDiopter(),
        beskrivelse = "Briller med sfærisk styrke på minst ett glass ≥ 1,00D ≤ 4,00D og/eller cylinderstyrke ≥ 1,00D ≤ 4,00D",
        beløp = "900"
    ),
    SATS_2(
        sfære = "4,25D".toDiopter().."6,00D".toDiopter(),
        beskrivelse = "Briller med sfærisk styrke på minst ett glass ≥ 4,25D ≤ 6,00D og cylinderstyrke ≤ 4,00D",
        beløp = "1800"
    ),
    SATS_3(
        sfære = "6,25D".toDiopter().."8,00D".toDiopter(),
        beskrivelse = "Briller med sfærisk styrke på minst ett glass ≥ 6,25D ≤ 8,00D og/eller cylinderstyrke ≥ 4,25D ≤ 6,00D",
        beløp = "2325"
    ),
    SATS_4(
        sfære = "8,25D".toDiopter().."10,00D".toDiopter(),
        beskrivelse = "Briller med sfærisk styrke på minst ett glass ≥ 8,25D ≤ 10,00D og cylinderstyrke ≤ 6,00D",
        beløp = "2700"
    ),
    SATS_5(
        sfære = "10,25D".toDiopter()..Diopter.MAX,
        beskrivelse = "Briller med sfærisk styrke på minst ett glass ≥ 10,25D og/eller cylinderstyrke ≥ 6,25D",
        beløp = "3975"
    ),
    INGEN(
        sfære = Diopter.ZERO..Diopter.ZERO,
        beskrivelse = "N/A",
        beløp = "N/A"
    )
}
