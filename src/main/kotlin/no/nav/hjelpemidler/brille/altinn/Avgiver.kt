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
    val rettigheter: Rettigheter = Rettigheter(),
    @JsonIgnore
    val harRoller: Boolean = false,
) {
    fun harRettighet(rettighet: Rettighet): Boolean =
        rettigheter.harRettighet(rettighet)
}
