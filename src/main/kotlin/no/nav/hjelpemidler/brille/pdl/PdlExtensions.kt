package no.nav.hjelpemidler.brille.pdl

import com.fasterxml.jackson.annotation.JsonAlias
import no.nav.hjelpemidler.brille.Configuration
import no.nav.hjelpemidler.brille.pdl.generated.enums.AdressebeskyttelseGradering
import no.nav.hjelpemidler.brille.pdl.generated.hentperson.Adressebeskyttelse
import no.nav.hjelpemidler.brille.pdl.generated.hentperson.Foedselsdato
import no.nav.hjelpemidler.brille.pdl.generated.hentperson.Navn
import java.time.LocalDate
import java.time.Month
import java.time.Period
import java.util.EnumSet

typealias Person = no.nav.hjelpemidler.brille.pdl.generated.hentperson.Person
typealias Barn = no.nav.hjelpemidler.brille.pdl.generated.medlemskaphentbarn.Person

// Støtt både gamle og nye pdl-datamodeller i databasen siden navnet på feltet foedselsdato har endret seg
data class PersonCompat(
    val navn: List<Navn>,
    @JsonAlias("foedsel")
    val foedselsdato: List<Foedselsdato>,
    val adressebeskyttelse: List<Adressebeskyttelse>,
) {
    fun asPerson() = Person(this.navn, this.foedselsdato, this.adressebeskyttelse)
}

private fun <T> List<T>.firstOrDefault(default: T): T = firstOrNull() ?: default

private fun String.capitalizeWord(): String = this
    .split(" ")
    .joinToString(" ") { word ->
        word.lowercase(Configuration.LOCALE)
            .replaceFirstChar { letter ->
                letter.titlecase(Configuration.LOCALE)
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

fun List<AdressebeskyttelseGradering>.erFortrolig() = any { gradering ->
    gradering in EnumSet
        .of(
            AdressebeskyttelseGradering.STRENGT_FORTROLIG,
            AdressebeskyttelseGradering.STRENGT_FORTROLIG_UTLAND,
            AdressebeskyttelseGradering.FORTROLIG,
        )
}

object HentPersonExtensions {
    fun Person.fødselsdato(): LocalDate? {
        val fødsel = foedselsdato.firstOrDefault(Foedselsdato())
        return when {
            fødsel.foedselsdato != null -> fødsel.foedselsdato
            fødsel.foedselsaar != null -> LocalDate.of(fødsel.foedselsaar, Month.DECEMBER, 31)
            else -> null
        }
    }

    fun Person.alder(): Int? {
        val fødselsdato = fødselsdato() ?: return null
        return Period.between(fødselsdato, LocalDate.now()).years
    }

    fun Person.alderPåDato(dato: LocalDate): Int? {
        val fødselsdato = fødselsdato() ?: return null
        return fødselsdato.until(dato).years
    }

    fun Person.navn(): String {
        val navn = navn.firstOrDefault(Navn("", "", ""))
        return listOfNotNull(navn.fornavn, navn.mellomnavn, navn.etternavn)
            .filterNot { it.isBlank() }
            .joinToString(" ")
            .capitalizeWord()
    }
}
