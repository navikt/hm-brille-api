package no.nav.hjelpemidler.brille.test

import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import no.nav.hjelpemidler.nare.regel.Regel
import no.nav.hjelpemidler.nare.regel.Regelevaluering
import no.nav.hjelpemidler.nare.regel.Regelutfall
import no.nav.hjelpemidler.nare.regel.Årsak
import java.time.LocalDate

fun Regelevaluering.liste(): List<Regelevaluering> =
    if (barn.isEmpty()) {
        listOf(this)
    } else {
        barn.flatMap(Regelevaluering::liste)
    }

fun <T : Any> Regelevaluering.verifiser(spesifikasjon: Regel<T>, matcher: Regelevaluering.() -> Unit) =
    liste().single { it.id == spesifikasjon.id }.should(matcher)

fun Regelevaluering.skalVærePositiv() = resultat shouldBe Regelutfall.JA
fun Regelevaluering.skalVæreNegativ() = resultat shouldBe Regelutfall.NEI

fun Regelevaluering.skalMangleDokumentasjon() = should {
    skalVæreNegativ()
    årsak shouldBe Årsak.DOKUMENTASJON_MANGLER
}

infix fun Int.`år på`(dato: LocalDate) = dato.minusYears(toLong())
