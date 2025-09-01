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
    val konkursdato: LocalDate? = null,
    val slettedato: LocalDate? = null,
) {
    fun næringskoder(): Set<Næringskode> = setOfNotNull(
        naeringskode1,
        naeringskode2,
        naeringskode3,
    )

    fun harNæringskode(kode: String): Boolean = næringskoder().any {
        it.kode == kode
    }
}

data class Postadresse(
    val postnummer: String?,
    val poststed: String,
    val adresse: List<String>,
)

data class Næringskode(
    val beskrivelse: String,
    val kode: String,
) {
    companion object {
        // Ny norsk standard for næringsfiltrering (SN2007 til SN2025): https://www.ssb.no/virksomheter-foretak-og-regnskap/metoder-og-dokumentasjon/ny-standard-for-naeringsgruppering-innfores-1.september-2025
        // (endrer til listOf med gammel kode i index-0, og alle nye koder påfølgende)
        val BUTIKKHANDEL_MED_OPTISKE_ARTIKLER = listOf("47.782", "47.740", "47.780", "47.920")
        val BUTIKKHANDEL_MED_GULL_OG_SØLVVARER = listOf("47.772", "47.770", "47.920")
        val BUTIKKHANDEL_MED_UR_OG_KLOKKER = listOf("47.771", "47.770", "47.920")
        val BUTIKKHANDEL_MED_HELSEKOST = listOf("47.291", "47.270", "47.920")
        val POSTORDRE_INTERNETTHANDEL_MED_ANNET_SPESIALISERT_VAREUTVALG = listOf("47.919", "47.210", "47.220", "47.230", "47.240", "47.250", "47.260", "47.520", "47.530", "47.631", "47.632", "47.640", "47.690", "47.730", "47.740", "47.750", "47.761", "47.762", "47.770", "47.780", "47.790", "47.920")
        val ANDRE_HELSETJENESTER = listOf("86.909", "86.941", "86.942", "86.950", "86.960", "86.991", "86.993")
        val ENGROSHANDEL_MED_OPTISKE_ARTIKLER = listOf("46.435", "46.430")
        val SPESIALISERT_LEGETJENESTE_UNNTATT_PSYKIATRISK_LEGETJENESTE = listOf("86.221")
    }
}
