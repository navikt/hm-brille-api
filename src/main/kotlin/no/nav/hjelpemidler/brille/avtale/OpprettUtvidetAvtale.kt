package no.nav.hjelpemidler.brille.avtale

data class OpprettUtvidetAvtale(
    val orgnr: String,
    val utvideAvtale: Boolean,
    val bilag1: Boolean,
    val bilag2: Boolean,
    val bilag3: Boolean,
    val bilag4: Boolean,
)
