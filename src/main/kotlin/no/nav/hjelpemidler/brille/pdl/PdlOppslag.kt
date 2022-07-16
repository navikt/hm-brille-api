package no.nav.hjelpemidler.brille.pdl

import com.fasterxml.jackson.databind.JsonNode
import no.nav.hjelpemidler.brille.pdl.generated.enums.AdressebeskyttelseGradering
import java.util.EnumSet

typealias Person = no.nav.hjelpemidler.brille.pdl.generated.hentperson.Person
typealias Barn = no.nav.hjelpemidler.brille.pdl.generated.medlemskaphentbarn.Person
typealias VergeEllerForelder = no.nav.hjelpemidler.brille.pdl.generated.medlemskaphentvergeellerforelder.Person

fun Person?.harAdressebeskyttelse(): Boolean =
    if (this == null) false else adressebeskyttelse.map { it.gradering }.erFortrolig()

fun Barn?.harAdressebeskyttelse(): Boolean =
    if (this == null) false else adressebeskyttelse.map { it.gradering }.erFortrolig()

fun VergeEllerForelder?.harAdressebeskyttelse(): Boolean =
    if (this == null) false else adressebeskyttelse.map { it.gradering }.erFortrolig()

fun List<AdressebeskyttelseGradering>.erFortrolig() = any { gradering ->
    EnumSet
        .of(
            AdressebeskyttelseGradering.STRENGT_FORTROLIG,
            AdressebeskyttelseGradering.STRENGT_FORTROLIG_UTLAND,
            AdressebeskyttelseGradering.FORTROLIG,
        )
        .contains(gradering)
}

data class PdlOppslag<T>(val data: T, val rawData: JsonNode)
