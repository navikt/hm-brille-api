package no.nav.hjelpemidler.brille.avtale

data class OppdaterAvtale(
    val kontonr: String,
    val epost: String,
    val epostBruksvilkar: String? = null,
)
