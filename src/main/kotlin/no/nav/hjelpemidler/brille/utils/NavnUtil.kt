package no.nav.hjelpemidler.brille.utils

import java.util.Locale

fun sammenlignEtternavn(etternavnEtt: String, etternavnTo: String): Boolean {
    val etternavnEttListe = etternavnEtt.split("-", " ").toList().map { it.lowercase(Locale.getDefault()) }
    val etternavnToListe = etternavnTo.split("-", " ").toList().map { it.lowercase(Locale.getDefault()) }

    return etternavnEttListe.intersect(etternavnToListe.toSet()).isNotEmpty()
}
