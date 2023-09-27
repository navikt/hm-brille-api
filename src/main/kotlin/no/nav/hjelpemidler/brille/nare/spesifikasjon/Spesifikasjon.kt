package no.nav.hjelpemidler.brille.nare.spesifikasjon

import no.nav.hjelpemidler.brille.nare.evaluering.Evaluering
import no.nav.hjelpemidler.brille.nare.evaluering.Evalueringer

data class Spesifikasjon<T>(
    val beskrivelse: String,
    val identifikator: String = "",
    val lovReferanse: String = "",
    val lovdataLenke: String = "",
    val grunnlag: Map<String, String> = emptyMap(),
    val barn: List<Spesifikasjon<T>> = emptyList(),
    val implementasjon: Evalueringer.(T) -> Evaluering,
) {
    fun evaluer(t: T): Evaluering = Evalueringer().run {
        evaluer(
            beskrivelse = beskrivelse,
            identifikator = identifikator,
            lovReferanse = lovReferanse,
            lovdataLenke = lovdataLenke,
            evaluering = implementasjon(this, t),
        )
    }

    infix fun og(annen: Spesifikasjon<T>): Spesifikasjon<T> = Spesifikasjon(
        beskrivelse = "$beskrivelse OG ${annen.beskrivelse}",
        barn = this.spesifikasjonEllerBarn() + annen.spesifikasjonEllerBarn(),
        implementasjon = { evaluer(it) og annen.evaluer(it) },
    )

    infix fun eller(annen: Spesifikasjon<T>): Spesifikasjon<T> = Spesifikasjon(
        beskrivelse = "$beskrivelse ELLER ${annen.beskrivelse}",
        barn = this.spesifikasjonEllerBarn() + annen.spesifikasjonEllerBarn(),
        implementasjon = { evaluer(it) eller annen.evaluer(it) },
    )

    fun ikke(): Spesifikasjon<T> = Spesifikasjon(
        beskrivelse = "IKKE $beskrivelse",
        identifikator = "IKKE $identifikator",
        barn = listOf(this),
        implementasjon = { evaluer(it).ikke() },
    )

    fun med(identifikator: String, beskrivelse: String): Spesifikasjon<T> =
        this.copy(identifikator = identifikator, beskrivelse = beskrivelse)

    private fun spesifikasjonEllerBarn(): List<Spesifikasjon<T>> = when {
        identifikator.isBlank() && barn.isNotEmpty() -> barn
        else -> listOf(this)
    }
}

fun <T> ikke(spesifikasjon: Spesifikasjon<T>) = spesifikasjon.ikke()
