package no.nav.hjelpemidler.brille.enhetsregisteret

@JvmInline
value class Organisasjonsnummer(private val value: String) :
    CharSequence by value {
    init {
        require(value.length == 9) {
            "Organisasjonsnummer må bestå av ni siffer"
        }
    }

    override fun toString(): String = value
}

data class Organisasjonsenhet(
    val organisasjonsnummer: String,
    val navn: String,
    val postadresse: Postadresse,
    val naeringskode1: Næringskode,
    val naeringskode2: Næringskode?,
    val naeringskode3: Næringskode?,
)

data class Postadresse(
    val postnummer: String,
    val poststed: String,
    val adresse: List<String>,
)

data class Næringskode(
    val beskrivelse: String,
    val kode: String,
)
