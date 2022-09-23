package no.nav.hjelpemidler.brille.enhetsregisteret

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.LocalDate

data class Organisasjonsenhet(
    @JsonProperty("organisasjonsnummer")
    val orgnr: String,
    val overordnetEnhet: String? = null,
    val navn: String,
    val forretningsadresse: Postadresse? = null, // orgenhet bruker forretningsadresse
    val beliggenhetsadresse: Postadresse? = null, // underenhet bruker beliggenhetsadresse
    val naeringskode1: Næringskode? = null,
    val naeringskode2: Næringskode? = null,
    val naeringskode3: Næringskode? = null,
    val slettedato: LocalDate? = null,
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
        const val ENGROSHANDEL_MED_OPTISKE_ARTIKLER = "46.435"
        const val SPESIALISERT_LEGETJENESTE_UNNTATT_PSYKIATRISK_LEGETJENESTE = "86.221"
    }
}
