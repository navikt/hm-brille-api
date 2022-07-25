package no.nav.hjelpemidler.brille.pdl

import no.nav.hjelpemidler.brille.Configuration
import no.nav.hjelpemidler.brille.pdl.generated.hentperson.Foedsel
import no.nav.hjelpemidler.brille.pdl.generated.hentperson.Navn
import java.time.LocalDate
import java.time.Month
import java.time.Period

private fun <T> List<T>.firstOrDefault(default: T): T = firstOrNull() ?: default

private fun String.capitalizeWord(): String = this
    .split(" ")
    .joinToString(" ") { word ->
        word.lowercase(Configuration.locale)
            .replaceFirstChar { letter ->
                letter.titlecase(Configuration.locale)
            }
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

    fun Person.navn(): String {
        val navn = navn.firstOrDefault(Navn("", "", ""))
        return listOfNotNull(navn.fornavn, navn.mellomnavn, navn.etternavn)
            .filterNot { it.isBlank() }
            .joinToString(" ")
            .capitalizeWord()
    }
}
