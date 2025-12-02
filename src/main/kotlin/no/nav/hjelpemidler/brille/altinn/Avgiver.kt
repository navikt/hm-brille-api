package no.nav.hjelpemidler.brille.altinn

import com.fasterxml.jackson.annotation.JsonProperty

data class Avgiver(
    @JsonProperty("Name")
    val navn: String,
    @JsonProperty("OrganizationNumber")
    val orgnr: String,
    @JsonProperty("ParentOrganizationNumber")
    val parentOrgnr: String?,
) {
    enum class Tjeneste(val kode: String, val versjon: Int) {
        /**
         * "Barnebrillestøtte - Inngå avtale om direkteoppgjør for optikerbedrift" (Generisk tilgangsressurs)
         *
         * Tjenesten lar optikerbedrifter inngå avtale med Nav for direkteoppgjør av tilskudd ved kjøp av briller til
         * barn. Når avtale er inngått kan autoriserte optikere som jobber i bedriften sende inn krav om brillestøtte
         * til barn på barnets vegne.
         *
         * Personer som får delegert denne rettigheten kan inngå avtale mellom bedriften som representeres og Nav om
         * direkteoppgjør av tilskudd ved kjøp av briller til barn.
         */
        OPPGJØRSAVTALE(kode = "5849", versjon = 1),

        /**
         * "Barnebrillestøtte - Se utbetalingsrapport" (Generisk tilgangsressurs)
         *
         * Tjenesten viser en oversikt over ventende og utførte utbetalinger av barnebrillestøtte gjennom
         * direkteoppgjørsordningen fra Nav til en bedrift. Rapporten viser informasjon som kan benyttes for å avstemme
         * mottatte utbetalinger fra Nav mot innsendte krav fra optikerbedrift.
         *
         * Personer med denne rettigheten får tilgang til utbetalingsrapporten og kan se alle ventende og utførte
         * utbetalinger til bedriften som representeres.
         */
        UTBETALINGSRAPPORT(kode = "5850", versjon = 1),
        ;

        override fun toString(): String = "[$kode,$versjon]"

        companion object {
            val FILTER: String = values().joinToString(" or ") {
                "ServiceCode eq '${it.kode}'"
            }

            fun fra(kode: String, versjon: Int): Tjeneste? = values().firstOrNull {
                it.kode == kode && it.versjon == versjon
            }
        }
    }
}
