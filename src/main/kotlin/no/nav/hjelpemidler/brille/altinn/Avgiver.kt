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
         * "Avtale om direkte oppgjør av briller for barn"
         */
        OPPGJØRSAVTALE(kode = "5849", versjon = 1),

        /**
         * "Utbetalingsrapport - brillestøtte"
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
