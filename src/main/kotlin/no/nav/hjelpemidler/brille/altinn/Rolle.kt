package no.nav.hjelpemidler.brille.altinn

enum class AltinnRolle(val kode: String) {
    HOVEDADMINISTRATOR("HADM"),
    REGNSKAPSMEDARBEIDER("REGNA"),
    REGNSKAPSFØRER("REGN"),
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
        1 -> query += " eq '${altinnRoller.first().kode}'"
        else -> {
            query += " eq '${altinnRoller.first().kode}'"
            val iter = altinnRoller.subList(1, altinnRoller.size).iterator()
            while (iter.hasNext()) {
                query += " or RoleDefinitionCode eq '${iter.next().kode}'"
            }
        }
    }
    return query
}
