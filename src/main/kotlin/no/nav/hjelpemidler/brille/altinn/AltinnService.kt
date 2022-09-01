package no.nav.hjelpemidler.brille.altinn

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import mu.KotlinLogging

private val sikkerLog = KotlinLogging.logger("tjenestekall")

class AltinnService(private val altinnClient: AltinnClient) {
    suspend fun hentAvgivere(fnr: String, rettighet: Rettighet, roller: AltinnRoller): List<Avgiver> =
        withContext(Dispatchers.IO) {
            val alleAvgivere = altinnClient.hentAvgivere(fnr)

            sikkerLog.info {
                "Hentet avgivere for fnr: $fnr, avgivere: ${alleAvgivere.map { it.orgnr }}"
            }

            val avgivere = alleAvgivere
                .map {
                    async {
                        it.copy(
                            rettigheter = hentRettigheter(fnr = fnr, orgnr = it.orgnr),
                            harRoller = altinnClient.harRolleFor(fnr = fnr, orgnr = it.orgnr, roller)
                        )
                    }
                }
                .awaitAll()

            val avgivereRettighet = avgivere.filter {
                it.harRettighet(rettighet) || it.harRoller
            }

            sikkerLog.info {
                "Avgivere hvor fnr: $fnr har rettighet: $rettighet, avgivere: ${avgivereRettighet.map { it.orgnr }}"
            }

            avgivereRettighet
        }

    suspend fun hentRettigheter(fnr: String, orgnr: String): Rettigheter = withContext(Dispatchers.IO) {
        val rettigheter = altinnClient.hentRettigheter(fnr, orgnr)
        sikkerLog.info {
            "Hentet rettigheter for fnr: $fnr, orgnr: $orgnr, rettigheter: ${rettigheter.rettigheter}"
        }
        rettigheter
    }

    suspend fun harRettighet(fnr: String, orgnr: String, rettighet: Rettighet): Boolean = withContext(Dispatchers.IO) {
        hentRettigheter(fnr, orgnr).harRettighet(rettighet)
    }

    suspend fun harTilgangTilOppgjørsavtale(fnr: String, orgnr: String): Boolean = withContext(Dispatchers.IO) {
        val roller = AltinnRoller(
            AltinnRolle.HOVEDADMINISTRATOR,
        )
        harRettighet(fnr, orgnr, Rettighet.OPPGJØRSAVTALE) || altinnClient.harRolleFor(fnr, orgnr, roller)
    }

    suspend fun harTilgangTilUtbetalingsrapport(fnr: String, orgnr: String): Boolean = withContext(Dispatchers.IO) {
        val roller = AltinnRoller(
            AltinnRolle.HOVEDADMINISTRATOR,
            AltinnRolle.REGNSKAPSMEDARBEIDER,
            AltinnRolle.REGNSKAPSFØRER,
        )
        harRettighet(fnr, orgnr, Rettighet.UTBETALINGSRAPPORT) || altinnClient.harRolleFor(fnr, orgnr, roller)
    }
}
