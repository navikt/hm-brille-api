package no.nav.hjelpemidler.brille.utils

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.hjelpemidler.brille.db.VedtakStore
import no.nav.hjelpemidler.brille.model.AvvisningsType
import no.nav.hjelpemidler.brille.pdl.PersonDetaljerDto

private val objectMapper = jacksonObjectMapper()
    .registerModule(JavaTimeModule())
    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)

class Vilkårsvurdering(val vedtakStore: VedtakStore) {
    fun kanSøke(personInformasjon: PersonDetaljerDto): Vilkår {
        // Sjekk om det allerede eksisterer et vedtak for barnet det siste året
        val harVedtak = vedtakStore.harFåttBrilleDetteKalenderÅret(personInformasjon.fnr)

        // Sjekk om man er for gammel for barnebriller
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

    fun json(): String = objectMapper.writeValueAsString(this)
}
