package no.nav.hjelpemidler.brille.altinn

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import mu.KotlinLogging

private val sikkerLog = KotlinLogging.logger("tjenestekall")

class AltinnService(private val altinnClient: AltinnClient) {
    suspend fun erHovedadministratorFor(fnr: String, orgnr: String): Boolean =
        altinnClient.erHovedadministratorFor(fnr, orgnr)

    suspend fun harRolleFor(fnr: String, orgnr: String, roller: AltinnRoller): Boolean =
        altinnClient.harRolleFor(fnr, orgnr, roller)

    suspend fun hentAvgivereHovedadministrator(fnr: String): List<Avgiver> = withContext(Dispatchers.IO) {
        val alleAvgivere = altinnClient.hentAvgivere(fnr)

        sikkerLog.info {
            "Hentet avgivere for fnr: $fnr, avgivere: ${alleAvgivere.map { it.orgnr }}"
        }

        val avgivere = alleAvgivere
            .map {
                async {
                    it.copy(harRolle = erHovedadministratorFor(fnr, it.orgnr))
                }
            }
            .awaitAll()

        val avgivereHovedadministrator = avgivere.filter {
            it.harRolle
        }

        sikkerLog.info {
            "Avgivere hvor fnr: $fnr er hovedadministrator, avgivere: ${avgivereHovedadministrator.map { it.orgnr }}"
        }

        avgivereHovedadministrator
    }

    suspend fun hentAvgivereMedRolle(fnr: String, roller: AltinnRoller): List<Avgiver> = withContext(Dispatchers.IO) {
        val alleAvgivere = altinnClient.hentAvgivere(fnr)

        sikkerLog.info {
            "Hentet avgivere for fnr: $fnr, avgivere: ${alleAvgivere.map { it.orgnr }}"
        }

        val avgivere = alleAvgivere
            .map {
                async {
                    it.copy(harRolle = harRolleFor(fnr, it.orgnr, roller))
                }
            }
            .awaitAll()

        val avgivereHovedadministrator = avgivere.filter {
            it.harRolle
        }

        sikkerLog.info {
            "Avgivere hvor fnr: $fnr er hovedadministrator, avgivere: ${avgivereHovedadministrator.map { it.orgnr }}"
        }

        avgivereHovedadministrator
    }
}
