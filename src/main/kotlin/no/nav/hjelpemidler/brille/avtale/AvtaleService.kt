package no.nav.hjelpemidler.brille.avtale

import mu.KotlinLogging
import no.nav.hjelpemidler.brille.altinn.AltinnService
import no.nav.hjelpemidler.brille.kafka.KafkaService
import no.nav.hjelpemidler.brille.virksomhet.Virksomhet
import no.nav.hjelpemidler.brille.virksomhet.VirksomhetStore
import java.time.LocalDateTime

private val log = KotlinLogging.logger { }

class AvtaleService(
    private val virksomhetStore: VirksomhetStore,
    private val altinnService: AltinnService,
    private val kafkaService: KafkaService,
) {
    suspend fun hentVirksomheter(fnr: String): List<Avtale> {
        val virksomheter = virksomhetStore.hentVirksomheterForInnsender(fnr).associateBy {
            it.orgnr
        }
        return altinnService.hentAvgivereHovedadministrator(fnr).map {
            Avtale(
                orgnr = it.orgnr,
                navn = it.navn,
                harNavAvtale = virksomheter[it.orgnr]?.harNavAvtale ?: false,
                kontonr = virksomheter[it.orgnr]?.kontonr,
                avtaleVersjon = virksomheter[it.orgnr]?.avtaleVersjon,
                opprettet = virksomheter[it.orgnr]?.opprettet,
            )
        }
    }

    fun opprettAvtale(fnr: String, opprettAvtale: OpprettAvtale) {
        log.info { "Oppretter avtale for orgnr: ${opprettAvtale.orgnr}" }
        val virksomhet = Virksomhet(
            orgnr = opprettAvtale.orgnr,
            kontonr = opprettAvtale.kontonr,
            fnrInnsender = fnr,
            navnInnsender = "", // fixme
            harNavAvtale = true,
            avtaleVersjon = null // fixme
        )
        virksomhetStore.lagreVirksomhet(
            virksomhet
        )
        kafkaService.avtaleOpprettet(opprettAvtale.orgnr, opprettAvtale.navn, virksomhet.opprettet)
    }
}

data class Avtale(
    val orgnr: String,
    val navn: String,
    val harNavAvtale: Boolean,
    val kontonr: String? = null,
    val avtaleVersjon: String? = null,
    val opprettet: LocalDateTime? = null,
)

data class OpprettAvtale(
    val orgnr: String,
    val navn: String,
    val kontonr: String,
)
