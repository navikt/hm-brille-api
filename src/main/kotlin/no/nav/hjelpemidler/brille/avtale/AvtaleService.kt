package no.nav.hjelpemidler.brille.avtale

import mu.KotlinLogging
import no.nav.hjelpemidler.brille.altinn.AltinnService
import no.nav.hjelpemidler.brille.kafka.KafkaService
import no.nav.hjelpemidler.brille.virksomhet.Virksomhet
import no.nav.hjelpemidler.brille.virksomhet.VirksomhetStore

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

    fun opprettAvtale(fnr: String, opprettAvtale: OpprettAvtale): Avtale {
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
        return Avtale(
            orgnr = virksomhet.orgnr,
            navn = opprettAvtale.navn,
            harNavAvtale = virksomhet.harNavAvtale,
            kontonr = virksomhet.kontonr,
            avtaleVersjon = virksomhet.avtaleVersjon,
            opprettet = virksomhet.opprettet,
        )
    }

    fun redigerAvtale(orgnr: String, redigerAvtale: RedigerAvtale): Avtale {
        val virksomhet = requireNotNull(virksomhetStore.hentVirksomhetForOrganisasjon(orgnr)) {
            "Fant ikke virksomhet med orgnr: $orgnr"
        }.copy(kontonr = redigerAvtale.kontonr)
        virksomhetStore.oppdaterKontonummer(orgnr, redigerAvtale.kontonr)
        return Avtale(
            orgnr = virksomhet.orgnr,
            navn = redigerAvtale.navn,
            harNavAvtale = virksomhet.harNavAvtale,
            kontonr = virksomhet.kontonr,
            avtaleVersjon = virksomhet.avtaleVersjon,
            opprettet = virksomhet.opprettet,
        )
    }
}
