package no.nav.hjelpemidler.brille.enhetsregisteret

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.core.JsonToken
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.http.contentLength
import io.ktor.utils.io.jvm.javaio.toInputStream
import mu.KotlinLogging
import no.nav.hjelpemidler.brille.db.DatabaseContext
import no.nav.hjelpemidler.brille.internal.MetricsConfig
import no.nav.hjelpemidler.brille.scheduler.LeaderElection
import no.nav.hjelpemidler.brille.scheduler.SimpleScheduler
import no.nav.hjelpemidler.http.createHttpClient
import java.time.LocalDate
import java.util.zip.GZIPInputStream
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

class EnhetsregisteretScheduler(
    private val enhetsregisteretService: EnhetsregisteretService,
    private val databaseContext: DatabaseContext,
    leaderElection: LeaderElection,
    metricsConfig: MetricsConfig,
    delay: Duration = 24.hours,
    onlyWorkHours: Boolean = true
) : SimpleScheduler(leaderElection, delay, metricsConfig, onlyWorkHours) {

    companion object {
        private val log = KotlinLogging.logger {}

        private val mapper = ObjectMapper().let { mapper ->
            mapper.registerKotlinModule()
            mapper.registerModule(JavaTimeModule())
            mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        }

        private val httpClient = createHttpClient {
            expectSuccess = true
            install(HttpTimeout) {
                requestTimeoutMillis = 10 * 60 * 1000
            }
        }
    }

    override suspend fun action() {
        runCatching {
            oppdaterHovedenheter()
            oppdaterUnderenheter()
        }.getOrElse { e ->
            log.error(e) { "Feil under oppdatering av vår kopi av enhetsregisteret" }
        }
    }

    suspend fun oppdaterHovedenheter() {
        log.info("Henter hovedenheter:")
        // API docs: https://data.brreg.no/enhetsregisteret/api/docs/index.html#enheter-lastned
        var c = 0
        httpClient.prepareGet("https://data.brreg.no/enhetsregisteret/api/enheter/lastned") {
            header(HttpHeaders.Accept, "application/vnd.brreg.enhetsregisteret.enhet.v1+gzip;charset=UTF-8")
        }.execute { httpResponse ->
            strømOgBlåsOpp<Organisasjonsenhet2>(httpResponse) { enhet ->
                log.info("Enhet navn: ${enhet.navn}")
                // db.save(enhet)
                c += 1
                c != 10
            }
        }
        log.info("Ferdig - hovedenheter")
    }

    suspend fun oppdaterUnderenheter() {
        log.info("Henter underenheter:")
        // API docs: https://data.brreg.no/enhetsregisteret/api/docs/index.html#underenheter-lastned
        var c = 0
        httpClient.prepareGet("https://data.brreg.no/enhetsregisteret/api/underenheter/lastned") {
            header(HttpHeaders.Accept, "application/vnd.brreg.enhetsregisteret.underenhet.v1+gzip;charset=UTF-8")
        }.execute { httpResponse ->
            strømOgBlåsOpp<Organisasjonsenhet2>(httpResponse) { underenhet ->
                log.info("Underenhet navn: ${underenhet.navn}")
                // db.save(underenhet)
                c += 1
                c != 10
            }
        }
        log.info("Ferdig - underenheter")
    }

    private suspend inline fun <reified T> strømOgBlåsOpp(httpResponse: HttpResponse, block: suspend (enhet: T) -> Boolean) {
        val contentLength = (httpResponse.contentLength() ?: 0) / 1024 / 1024
        log.info("Komprimert filstørrelse: $contentLength MiB")

        val gunzipStream = GZIPInputStream(httpResponse.bodyAsChannel().toInputStream())
        mapper.factory.createParser(gunzipStream).use { jsonParser ->
            // Check the first token
            check(jsonParser.nextToken() === JsonToken.START_ARRAY) { "Expected content to be an array" }

            // Iterate over the tokens until the end of the array
            while (jsonParser.nextToken() !== JsonToken.END_ARRAY) {
                // Read a Organisasjonsenhet2 instance using ObjectMapper and do something with it
                val enhet: T = mapper.readValue(jsonParser)
                if (!block(enhet)) {
                    break
                }
            }
        }
    }

}

data class Organisasjonsenhet2(
    @JsonProperty("organisasjonsnummer")
    val orgnr: String,
    val overordnetEnhet: String? = null,
    val navn: String,
    val forretningsadresse: Postadresse2? = null, // orgenhet bruker forretningsadresse
    val beliggenhetsadresse: Postadresse2? = null, // underenhet bruker beliggenhetsadresse
    val naeringskode1: Næringskode2? = null,
    val naeringskode2: Næringskode2? = null,
    val naeringskode3: Næringskode2? = null,
    val slettedato: LocalDate? = null,
) {
    fun næringskoder(): Set<Næringskode2> = setOfNotNull(
        naeringskode1,
        naeringskode2,
        naeringskode3
    )

    fun harNæringskode(kode: String): Boolean = næringskoder().any {
        it.kode == kode
    }
}

data class Postadresse2(
    val postnummer: String?,
    val poststed: String,
    val adresse: List<String>,
)

data class Næringskode2(
    val beskrivelse: String,
    val kode: String,
) {
    companion object {
        const val BUTIKKHANDEL_MED_OPTISKE_ARTIKLER = "47.782"
        const val BUTIKKHANDEL_MED_GULL_OG_SØLVVARER = "47.772"
        const val BUTIKKHANDEL_MED_UR_OG_KLOKKER = "47.771"
        const val BUTIKKHANDEL_MED_HELSEKOST = "47.291"
        const val ANDRE_HELSETJENESTER = "86.909"
        const val ENGROSHANDEL_MED_OPTISKE_ARTIKLER = "46.435"
        const val SPESIALISERT_LEGETJENESTE_UNNTATT_PSYKIATRISK_LEGETJENESTE = "86.221"
    }
}
