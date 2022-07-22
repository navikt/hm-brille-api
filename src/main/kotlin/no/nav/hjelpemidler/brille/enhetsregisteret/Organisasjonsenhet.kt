package no.nav.hjelpemidler.brille.enhetsregisteret

import com.fasterxml.jackson.annotation.JsonProperty

data class Organisasjonsenhet(
    @JsonProperty("organisasjonsnummer")
    val orgnr: String,
    val overordnetEnhet: String?,
    val navn: String,
    val forretningsadresse: Postadresse?, // orgenhet bruker forretningsadresse
    val beliggenhetsadresse: Postadresse?, // underenhet bruker beliggenhetsadresse
    val naeringskode1: Næringskode?,
    val naeringskode2: Næringskode?,
    val naeringskode3: Næringskode?,
) {
    fun næringskoder(): Set<Næringskode> = setOfNotNull(
        naeringskode1,
        naeringskode2,
        naeringskode3
    )

    fun harNæringskode(kode: String): Boolean = næringskoder().any {
        it.kode == kode
    }
}

data class Postadresse(
    val postnummer: String,
    val poststed: String,
    val adresse: List<String>,
)

data class Næringskode(
    val beskrivelse: String,
    val kode: String,
) {
    companion object {
        const val BUTIKKHANDEL_MED_OPTISKE_ARTIKLER = "47.782"
        const val BUTIKKHANDEL_MED_GULL_OG_SØLVVARER = "47.772"
        const val BUTIKKHANDEL_MED_UR_OG_KLOKKER = "47.771"
        const val BUTIKKHANDEL_MED_HELSEKOST = "47.291"
        const val ANDRE_HELSETJENESTER = "86.909"
    }
}
