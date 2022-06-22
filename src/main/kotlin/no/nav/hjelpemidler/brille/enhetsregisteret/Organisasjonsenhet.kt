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
)
