package no.nav.hjelpemidler.brille.avtale

import mu.KotlinLogging
import no.nav.hjelpemidler.brille.altinn.AltinnService
import no.nav.hjelpemidler.brille.altinn.Rettighet
import no.nav.hjelpemidler.brille.db.DatabaseContext
import no.nav.hjelpemidler.brille.db.transaction
import no.nav.hjelpemidler.brille.enhetsregisteret.EnhetsregisteretService
import no.nav.hjelpemidler.brille.enhetsregisteret.Næringskode
import no.nav.hjelpemidler.brille.enhetsregisteret.Organisasjonsenhet
import no.nav.hjelpemidler.brille.kafka.KafkaService
import no.nav.hjelpemidler.brille.virksomhet.Virksomhet
import java.time.LocalDateTime

private val log = KotlinLogging.logger { }
private val sikkerLog = KotlinLogging.logger("tjenestekall")

class AvtaleService(
    val databaseContext: DatabaseContext,
    private val altinnService: AltinnService,
    private val enhetsregisteretService: EnhetsregisteretService,
    private val kafkaService: KafkaService,
) {
    suspend fun hentOrganisasjonsenhet(orgnr: String): Organisasjonsenhet =
        requireNotNull(enhetsregisteretService.hentOrganisasjonsenhet(orgnr)) {
            "Fant ikke organisasjonsenhet med orgnr: $orgnr"
        }

    suspend fun hentVirksomheter(fnrInnsender: String, rettighet: Rettighet): List<Avtale> {
        val avgivereFiltrert = altinnService.hentAvgivereMedRettighet(fnrInnsender, rettighet)
            .filter { avgiver ->
                val orgnr = avgiver.orgnr
                val enhet = enhetsregisteretService.hentOrganisasjonsenhet(orgnr)
                if (enhet == null) {
                    false
                } else {
                    log.info {
                        "orgnr: $orgnr, næringskoder: ${enhet.næringskoder().map { it.kode }}"
                    }
                    setOf(
                        Næringskode.BUTIKKHANDEL_MED_OPTISKE_ARTIKLER,
                        Næringskode.BUTIKKHANDEL_MED_GULL_OG_SØLVVARER,
                        Næringskode.BUTIKKHANDEL_MED_UR_OG_KLOKKER,
                        Næringskode.BUTIKKHANDEL_MED_HELSEKOST,
                        Næringskode.ANDRE_HELSETJENESTER,
                        Næringskode.ENGROSHANDEL_MED_OPTISKE_ARTIKLER,
                        Næringskode.SPESIALISERT_LEGETJENESTE_UNNTATT_PSYKIATRISK_LEGETJENESTE
                    ).any { enhet.harNæringskode(it) }
                }
            }

        sikkerLog.info {
            "fnrInnsender: $fnrInnsender har rettighet: $rettighet for: ${avgivereFiltrert.map { it.orgnr }}"
        }

        val virksomheter =
            transaction(databaseContext) { ctx ->
                ctx.virksomhetStore.hentVirksomheterForOrganisasjoner(avgivereFiltrert.map { it.orgnr }).associateBy {
                    it.orgnr
                }
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

        if (!altinnService.harRettighetOppgjørsavtale(fnrInnsender, orgnr)) {
            throw AvtaleManglerTilgangException(orgnr)
        }

        log.info { "Oppretter avtale for orgnr: $orgnr" }
        sikkerLog.info { "fnrInnsender: $fnrInnsender, opprettAvtale: $opprettAvtale" }

        val virksomhet = transaction(databaseContext) { ctx ->
            ctx.virksomhetStore.lagreVirksomhet(
                Virksomhet(
                    orgnr = orgnr,
                    kontonr = opprettAvtale.kontonr,
                    epost = opprettAvtale.epost,
                    fnrInnsender = fnrInnsender,
                    navnInnsender = "", // todo -> slett
                    aktiv = true,
                    avtaleversjon = null
                )
            )
        }

        val organisasjonsenhet = hentOrganisasjonsenhet(orgnr)
        val avtale = Avtale(virksomhet = virksomhet, navn = organisasjonsenhet.navn)

        kafkaService.avtaleOpprettet(avtale)

        return avtale
    }

    suspend fun oppdaterAvtale(fnrOppdatertAv: String, orgnr: String, oppdaterAvtale: OppdaterAvtale): Avtale {
        if (!altinnService.harRettighetOppgjørsavtale(fnrOppdatertAv, orgnr)) {
            throw AvtaleManglerTilgangException(orgnr)
        }

        log.info { "Oppdaterer avtale for orgnr: $orgnr" }
        sikkerLog.info { "fnrOppdatertAv: $fnrOppdatertAv, orgnr: $orgnr, oppdaterAvtale: $oppdaterAvtale" }

        val virksomhet = transaction(databaseContext) { ctx ->
            ctx.virksomhetStore.oppdaterVirksomhet(
                requireNotNull(ctx.virksomhetStore.hentVirksomhetForOrganisasjon(orgnr)) {
                    "Fant ikke virksomhet med orgnr: $orgnr"
                }.copy(
                    kontonr = oppdaterAvtale.kontonr,
                    epost = oppdaterAvtale.epost,
                    fnrOppdatertAv = fnrOppdatertAv,
                    oppdatert = LocalDateTime.now(),
                )
            )
        }

        if (virksomhet.fnrInnsender != virksomhet.fnrOppdatertAv) {
            sikkerLog.warn {
                "Avtalen ble oppdatert av en annen en innsender, fnrInnsender: ${virksomhet.fnrInnsender}, fnrOppdatertAv: ${virksomhet.fnrOppdatertAv}"
            }
        }

        val organisasjonsenhet = hentOrganisasjonsenhet(orgnr)
        val avtale = Avtale(virksomhet = virksomhet, navn = organisasjonsenhet.navn)

        kafkaService.avtaleOppdatert(avtale)

        return avtale
    }
}
