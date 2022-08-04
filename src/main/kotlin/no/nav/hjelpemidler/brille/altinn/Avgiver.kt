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
    val harRolle: Boolean = false,
)

enum class AltinnRolle(val kode: String) {
    HOVEDADMINISTRATOR("HADM"),
    REGNSKAPSMEDARBEIDER("REGNA")
}

class AltinnRoller(vararg params: AltinnRolle) : List<AltinnRolle> by params.toList() {
    private val items = params.toList()
    init {
        require(items.isNotEmpty()) { "Rolleliste kan ikke være tom" }
    }
}

internal fun buildRolleQuery(altinnRoller: AltinnRoller): String {

    var query = ""

    when (altinnRoller.size) {
        1 -> query = query.plus(" eq '${altinnRoller.first().kode}'")
        else -> {
            query = query.plus(" eq '${altinnRoller.first().kode}'")
            val iter = altinnRoller.subList(1, altinnRoller.size).iterator()
            while (iter.hasNext()) {
                query = query.plus(" or RoleDefinitionCode eq '${iter.next().kode}'")
            }
        }
    }

    return query
}
