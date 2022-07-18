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
    suspend fun hentVirksomheter(fnrInnsender: String): List<Avtale> {
        val virksomheter = virksomhetStore.hentVirksomheterForInnsender(fnrInnsender).associateBy {
            it.orgnr
        }
        return altinnService.hentAvgivereHovedadministrator(fnrInnsender).map {
            Avtale(
                orgnr = it.orgnr,
                navn = it.navn,
                aktiv = virksomheter[it.orgnr]?.aktiv ?: false,
                kontonr = virksomheter[it.orgnr]?.kontonr,
                avtaleversjon = virksomheter[it.orgnr]?.avtaleversjon,
                opprettet = virksomheter[it.orgnr]?.opprettet,
                oppdatert = virksomheter[it.orgnr]?.oppdatert
            )
        }
    }

    suspend fun opprettAvtale(fnrInnsender: String, opprettAvtale: OpprettAvtale): Avtale {
        val orgnr = opprettAvtale.orgnr
        if (!altinnService.erHovedadministratorFor(fnrInnsender, orgnr)) {
            throw AvtaleManglerTilgangException(orgnr)
        }
        log.info { "Oppretter avtale for orgnr: $orgnr" }
        val virksomhet = virksomhetStore.lagreVirksomhet(
            Virksomhet(
                orgnr = orgnr,
                kontonr = opprettAvtale.kontonr,
                fnrInnsender = fnrInnsender,
                navnInnsender = "", // fixme
                aktiv = true,
                avtaleversjon = null // fixme
            )
        )
        kafkaService.avtaleOpprettet(orgnr, opprettAvtale.navn, virksomhet.opprettet)
        return Avtale(
            orgnr = virksomhet.orgnr,
            navn = opprettAvtale.navn,
            aktiv = virksomhet.aktiv,
            kontonr = virksomhet.kontonr,
            avtaleversjon = virksomhet.avtaleversjon,
            opprettet = virksomhet.opprettet,
            oppdatert = virksomhet.oppdatert
        )
    }

    suspend fun redigerAvtale(fnrInnsender: String, orgnr: String, redigerAvtale: RedigerAvtale): Avtale {
        if (!altinnService.erHovedadministratorFor(fnrInnsender, orgnr)) {
            throw AvtaleManglerTilgangException(orgnr)
        }
        val virksomhet = requireNotNull(virksomhetStore.hentVirksomhetForOrganisasjon(orgnr)) {
            "Fant ikke virksomhet med orgnr: $orgnr"
        }.copy(kontonr = redigerAvtale.kontonr)
        virksomhetStore.oppdaterKontonummer(orgnr, redigerAvtale.kontonr)
        return Avtale(
            orgnr = virksomhet.orgnr,
            navn = redigerAvtale.navn,
            aktiv = virksomhet.aktiv,
            kontonr = virksomhet.kontonr,
            avtaleversjon = virksomhet.avtaleversjon,
            opprettet = virksomhet.opprettet,
            oppdatert = virksomhet.oppdatert
        )
    }
}
