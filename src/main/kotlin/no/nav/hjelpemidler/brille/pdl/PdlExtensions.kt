package no.nav.hjelpemidler.brille.pdl

import no.nav.hjelpemidler.brille.Configuration
import no.nav.hjelpemidler.brille.pdl.generated.enums.AdressebeskyttelseGradering
import no.nav.hjelpemidler.brille.pdl.generated.hentperson.Foedsel
import no.nav.hjelpemidler.brille.pdl.generated.hentperson.Navn
import java.time.LocalDate
import java.time.Month
import java.time.Period
import java.util.EnumSet

typealias Person = no.nav.hjelpemidler.brille.pdl.generated.hentperson.Person
typealias Barn = no.nav.hjelpemidler.brille.pdl.generated.medlemskaphentbarn.Person
typealias VergeEllerForelder = no.nav.hjelpemidler.brille.pdl.generated.medlemskaphentvergeellerforelder.Person

private fun <T> List<T>.firstOrDefault(default: T): T = firstOrNull() ?: default

private fun String.capitalizeWord(): String = this
    .split(" ")
    .joinToString(" ") { word ->
        word.lowercase(Configuration.locale)
            .replaceFirstChar { letter ->
                letter.titlecase(Configuration.locale)
            }
    }

fun Person?.harAdressebeskyttelse(): Boolean =
    when {
        this == null -> false
        else -> adressebeskyttelse.map { it.gradering }.erFortrolig()
    }

fun Barn?.harAdressebeskyttelse(): Boolean =
    when {
        this == null -> false
        else -> adressebeskyttelse.map { it.gradering }.erFortrolig()
    }

fun VergeEllerForelder?.harAdressebeskyttelse(): Boolean =
    when {
        this == null -> false
        else -> adressebeskyttelse.map { it.gradering }.erFortrolig()
    }

fun List<AdressebeskyttelseGradering>.erFortrolig() = any { gradering ->
    gradering in EnumSet
        .of(
            AdressebeskyttelseGradering.STRENGT_FORTROLIG,
            AdressebeskyttelseGradering.STRENGT_FORTROLIG_UTLAND,
            AdressebeskyttelseGradering.FORTROLIG,
        )
}

object HentPersonExtensions {
    fun Person.fodselsdato(): LocalDate? {
        val fodsel = foedsel.firstOrDefault(Foedsel())
        return when {
            fodsel.foedselsdato != null -> fodsel.foedselsdato
            fodsel.foedselsaar != null -> LocalDate.of(fodsel.foedselsaar, Month.DECEMBER, 31)
            else -> null
        }
    }

    fun Person.alder(): Int? {
        val fodselsdato = fodselsdato() ?: return null
        return Period.between(fodselsdato, LocalDate.now()).years
    }

    fun Person.alderPåDato(dato: LocalDate): Int? {
        val fodselsdato = fodselsdato() ?: return null
        return Period.between(fodselsdato, dato).years
    }

    fun Person.navn(): String {
        val navn = navn.firstOrDefault(Navn("", "", ""))
        return listOfNotNull(navn.fornavn, navn.mellomnavn, navn.etternavn)
            .filterNot { it.isBlank() }
            .joinToString(" ")
            .capitalizeWord()
    }
}
