package no.nav.hjelpemidler.brille.utils

import no.nav.hjelpemidler.brille.db.VedtakStore
import no.nav.hjelpemidler.brille.model.AvvisningsType
import no.nav.hjelpemidler.brille.pdl.model.PersonDetaljerDto

class Vilkårsvurdering(val vedtakStore: VedtakStore) {
    suspend fun kanSøke(personInformasjon: PersonDetaljerDto): Vilkår {
        // Sjekk om det allerede eksisterer et vedtak for barnet det siste året
        val harVedtak = vedtakStore.harFåttBrilleSisteÅret(personInformasjon.fnr)

        // Slå opp personinformasjon om barnet
        val forGammel =
            personInformasjon.alder!! > 17 /* Arbeidshypotese fra forskrift: krav må komme før fylte 18 år */

        return Vilkår(forGammel, harVedtak)
    }
}

data class Vilkår(
    val forGammel: Boolean,
    val harVedtak: Boolean,
) {
    fun avvisningsGrunner(): List<AvvisningsType> {
        val avvisningsTyper = mutableListOf<AvvisningsType>()

        if (forGammel) avvisningsTyper.add(AvvisningsType.ALDER)
        if (harVedtak) avvisningsTyper.add(AvvisningsType.HAR_VEDTAK_I_ÅR)

        return avvisningsTyper
    }

    fun valider(): Boolean {
        return avvisningsGrunner().isEmpty()
    }
}
