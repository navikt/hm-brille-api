package no.nav.hjelpemidler.brille.avtale

import mu.KotlinLogging
import no.nav.hjelpemidler.brille.altinn.AltinnService
import no.nav.hjelpemidler.brille.enhetsregisteret.EnhetsregisteretService
import no.nav.hjelpemidler.brille.enhetsregisteret.Næringskode
import no.nav.hjelpemidler.brille.kafka.KafkaService
import no.nav.hjelpemidler.brille.virksomhet.Virksomhet
import no.nav.hjelpemidler.brille.virksomhet.VirksomhetStore

private val log = KotlinLogging.logger { }
private val sikkerLog = KotlinLogging.logger("tjenestekall")

class AvtaleService(
    private val virksomhetStore: VirksomhetStore,
    private val altinnService: AltinnService,
    private val enhetsregisteretService: EnhetsregisteretService,
    private val kafkaService: KafkaService,
) {
    suspend fun hentVirksomheter(fnrInnsender: String): List<Avtale> {
        val avgivereFiltrert = altinnService.hentAvgivereHovedadministrator(fnrInnsender)
            .filter { avgiver ->
                val orgnr = avgiver.orgnr
                val enhet = enhetsregisteretService.hentOrganisasjonsenhet(orgnr)
                if (enhet == null) {
                    false
                } else {
                    log.info {
                        "orgnr: $orgnr, næringskoder: ${enhet.næringskoder().map { it.kode }}"
                    }
                    enhet.harNæringskode(Næringskode.BUTIKKHANDEL_MED_OPTISKE_ARTIKLER) ||
                        enhet.harNæringskode(Næringskode.ANDRE_HELSETJENESTER)
                }
            }
        sikkerLog.info {
            "fnrInnsender: $fnrInnsender kan opprette avtale for: ${avgivereFiltrert.map { it.orgnr }}"
        }
        val virksomheter = virksomhetStore.hentVirksomheterForInnsender(fnrInnsender).associateBy {
            it.orgnr
        }
        return avgivereFiltrert
            .map {
                Avtale(
                    orgnr = it.orgnr,
                    navn = it.navn,
                    aktiv = virksomheter[it.orgnr]?.aktiv ?: false,
                    kontonr = virksomheter[it.orgnr]?.kontonr,
                    epost = virksomheter[it.orgnr]?.epost,
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
                epost = opprettAvtale.epost,
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
            epost = virksomhet.epost,
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
        }.copy(kontonr = redigerAvtale.kontonr, epost = redigerAvtale.epost)
        virksomhetStore.oppdaterKontonummerOgEpost(orgnr, redigerAvtale.kontonr, redigerAvtale.epost)
        return Avtale(
            orgnr = virksomhet.orgnr,
            navn = redigerAvtale.navn,
            aktiv = virksomhet.aktiv,
            kontonr = virksomhet.kontonr,
            epost = virksomhet.epost,
            avtaleversjon = virksomhet.avtaleversjon,
            opprettet = virksomhet.opprettet,
            oppdatert = virksomhet.oppdatert
        )
    }
}
