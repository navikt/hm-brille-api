package no.nav.hjelpemidler.brille.nare.evaluering

import no.nav.hjelpemidler.brille.Configuration

data class Evaluering(
    val resultat: Resultat,
    val begrunnelse: String,
    val beskrivelse: String = "",
    val identifikator: String = "",
    val operator: Operator = Operator.INGEN,
    var barn: List<Evaluering> = emptyList(),
    val programvareVersjon: String = Configuration.gitCommit,
) {
    infix fun og(annen: Evaluering) = Evaluering(
        resultat = resultat og annen.resultat,
        begrunnelse = "($begrunnelse OG ${annen.begrunnelse})",
        operator = Operator.OG,
        barn = this.spesifikasjonEllerBarn() + annen.spesifikasjonEllerBarn()
    )

    infix fun eller(annen: Evaluering) = Evaluering(
        resultat = resultat eller annen.resultat,
        begrunnelse = "($begrunnelse ELLER ${annen.begrunnelse})",
        operator = Operator.ELLER,
        barn = this.spesifikasjonEllerBarn() + annen.spesifikasjonEllerBarn()
    )

    fun ikke() = Evaluering(
        resultat = resultat.ikke(),
        begrunnelse = "(IKKE $begrunnelse)",
        operator = Operator.IKKE,
        barn = listOf(this)
    )

    private fun spesifikasjonEllerBarn(): List<Evaluering> = when {
        identifikator.isBlank() && barn.isNotEmpty() -> barn
        else -> listOf(this)
    }
}
