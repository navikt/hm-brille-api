package no.nav.hjelpemidler.brille.pdl.model

data class PersonGraphqlQuery(
    val query: String,
    val variables: Variables,
)

data class Variables(
    val ident: String,
)

fun hentPersonQuery(fnummer: String): PersonGraphqlQuery {
    val query = PersonGraphqlQuery::class.java.getResource("/pdl/hentPerson.graphql").readText().replace("[\n\r]", "")
        .replace("[\n]", "")
    return PersonGraphqlQuery(query, Variables(ident = fnummer))
}

fun hentIdenterQuery(fnummer: String): PersonGraphqlQuery {
    val query = PersonGraphqlQuery::class.java.getResource("/pdl/hentAktor.graphql").readText().replace("[\n]", "")
    return PersonGraphqlQuery(query, Variables(ident = fnummer))
}

fun hentPersonDetaljerQuery(fnummer: String): PersonGraphqlQuery {
    val query =
        PersonGraphqlQuery::class.java.getResource("/pdl/hentPersonDetaljer.graphql").readText().replace("[\n]", "")
    return PersonGraphqlQuery(query, Variables(ident = fnummer))
}
