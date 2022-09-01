package no.nav.hjelpemidler.brille.altinn

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty

data class Avgiver(
    @JsonProperty("Name")
    val navn: String,
    @JsonProperty("OrganizationNumber")
    val orgnr: String,
    @JsonProperty("ParentOrganizationNumber")
    val parentOrgnr: String?,
    @JsonIgnore
    val rettigheter: Set<Rettighet> = emptySet(),
    @JsonIgnore
    val roller: Set<Rolle> = emptySet(),
) {
    fun harRettighet(rettighet: Rettighet): Boolean =
        rettigheter.contains(rettighet)

    fun harRolle(rolle: Rolle): Boolean =
        roller.contains(rolle)

    fun harTilgangTilOppgjørsavtale(): Boolean =
        harRettighet(Rettighet.OPPGJØRSAVTALE) ||
                harRolle(Rolle.HOVEDADMINISTRATOR)

    fun harTilgangTilUtbetalingsrapport(): Boolean =
        harRettighet(Rettighet.UTBETALINGSRAPPORT) ||
                harRolle(Rolle.HOVEDADMINISTRATOR) ||
                harRolle(Rolle.REGNSKAPSMEDARBEIDER) ||
                harRolle(Rolle.REGNSKAPSFØRER)

    enum class Rettighet(val kode: String, val versjon: Int) {
        /**
         * "Avtale om direkte oppgjør av briller for barn"
         */
        OPPGJØRSAVTALE(kode = "5849", versjon = 1),

        /**
         * "Utbetalingsrapport - brillestøtte"
         */
        UTBETALINGSRAPPORT(kode = "5850", versjon = 1);

        companion object {
            val FILTER: String = values().joinToString(" or ") {
                "ServiceCode eq '${it.kode}'"
            }

            fun fra(kode: String, versjon: Int): Rettighet? = values().firstOrNull {
                it.kode == kode && it.versjon == versjon
            }
        }
    }

    enum class Rolle(val kode: String) {
        HOVEDADMINISTRATOR(kode = "HADM"),
        REGNSKAPSMEDARBEIDER(kode = "REGNA"),
        REGNSKAPSFØRER(kode = "REGN");

        companion object {
            val FILTER: String = values().joinToString(" or ") {
                "RoleDefinitionCode eq '${it.kode}'"
            }

            fun fra(kode: String): Rolle? = values().firstOrNull {
                it.kode == kode
            }
        }
    }
}
