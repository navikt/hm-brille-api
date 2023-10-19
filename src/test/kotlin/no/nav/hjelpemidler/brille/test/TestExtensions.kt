package no.nav.hjelpemidler.brille.test

import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import no.nav.hjelpemidler.nare.evaluering.Evaluering
import no.nav.hjelpemidler.nare.evaluering.Resultat
import no.nav.hjelpemidler.nare.evaluering.Årsak
import no.nav.hjelpemidler.nare.spesifikasjon.Spesifikasjon
import java.time.LocalDate

fun Evaluering.toList(): List<Evaluering> = when {
    barn.isEmpty() -> listOf(this)
    else -> barn.flatMap(Evaluering::toList)
}

fun <T> Evaluering.verifiser(spesifikasjon: Spesifikasjon<T>, matcher: Evaluering.() -> Unit) =
    toList().single { it.identifikator == spesifikasjon.identifikator }.should(matcher)

fun Evaluering.skalVærePositiv() = resultat shouldBe Resultat.JA
fun Evaluering.skalVæreNegativ() = resultat shouldBe Resultat.NEI

fun Evaluering.skalMangleDokumentasjon() = should {
    skalVæreNegativ()
    årsak shouldBe Årsak.DOKUMENTASJON_MANGLER
}

infix fun Int.`år på`(dato: LocalDate) = dato.minusYears(toLong())
