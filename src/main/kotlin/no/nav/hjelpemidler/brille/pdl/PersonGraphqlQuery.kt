package no.nav.hjelpemidler.brille.pdl

data class PersonGraphqlQuery(
    val query: String,
    val variables: Variables,
)

data class Variables(
    val ident: String,
)

fun hentPersonQuery(fnummer: String): PersonGraphqlQuery {
    val query =
        PersonGraphqlQuery::class.java.getResource("/pdl/hentPerson.graphql").readText().replace("[\n]", "")
    return PersonGraphqlQuery(query, Variables(ident = fnummer))
}

fun medlemskapHentBarnQuery(fnummer: String): PersonGraphqlQuery {
    val query =
        PersonGraphqlQuery::class.java.getResource("/pdl/medlemskapHentBarnQuery.graphql").readText().replace("[\n]", "")
    return PersonGraphqlQuery(query, Variables(ident = fnummer))
}

fun medlemskapHentVergeEllerForelderQuery(fnummer: String): PersonGraphqlQuery {
    val query =
        PersonGraphqlQuery::class.java.getResource("/pdl/medlemskapHentVergeEllerForelderQuery.graphql").readText().replace("[\n]", "")
    return PersonGraphqlQuery(query, Variables(ident = fnummer))
}
