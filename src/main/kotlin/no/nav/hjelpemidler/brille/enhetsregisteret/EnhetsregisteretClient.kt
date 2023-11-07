package no.nav.hjelpemidler.brille.enhetsregisteret

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
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import no.nav.hjelpemidler.brille.Configuration
import no.nav.hjelpemidler.brille.db.DatabaseContext
import no.nav.hjelpemidler.brille.db.transaction
import no.nav.hjelpemidler.http.createHttpClient
import java.util.zip.GZIPInputStream
import kotlin.system.measureTimeMillis

private val log = KotlinLogging.logger { }

class EnhetsregisteretClient(
    props: Configuration.EnhetsregisteretProperties,
    val databaseContext: DatabaseContext,
) {
    private val baseUrl = props.baseUrl

    private val mapper = ObjectMapper().let { mapper ->
        mapper.registerKotlinModule()
        mapper.registerModule(JavaTimeModule())
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        mapper
    }

    private val httpClient = createHttpClient {
        expectSuccess = true
        install(HttpTimeout) {
            requestTimeoutMillis = 60 * 60 * 1000
        }
    }

    suspend fun oppdaterMirror() {
        var c = 0
        log.info("Oppdater mirror - Henter hoved-/underenheter:")
        val elapsed = measureTimeMillis {
            transaction(databaseContext) {
                it.enhetsregisteretStore.oppdaterEnheter { lagre ->
                    // API docs: https://data.brreg.no/enhetsregisteret/api/docs/index.html#enheter-lastned
                    runBlocking {
                        httpClient.prepareGet("$baseUrl/enhetsregisteret/api/enheter/lastned") {
                            header(
                                HttpHeaders.Accept,
                                "application/vnd.brreg.enhetsregisteret.enhet.v1+gzip;charset=UTF-8",
                            )
                        }.execute { httpResponse ->
                            strømOgBlåsOpp<Organisasjonsenhet>(httpResponse) { enhetChunk ->
                                lagre(EnhetType.HOVEDENHET, enhetChunk)
                                c += enhetChunk.count()
                            }
                        }
                    }

                    // API docs: https://data.brreg.no/enhetsregisteret/api/docs/index.html#underenheter-lastned
                    runBlocking {
                        httpClient.prepareGet("$baseUrl/enhetsregisteret/api/underenheter/lastned") {
                            header(
                                HttpHeaders.Accept,
                                "application/vnd.brreg.enhetsregisteret.underenhet.v1+gzip;charset=UTF-8",
                            )
                        }.execute { httpResponse ->
                            strømOgBlåsOpp<Organisasjonsenhet>(httpResponse) { underenhetChunk ->
                                lagre(EnhetType.UNDERENHET, underenhetChunk)
                                c += underenhetChunk.count()
                            }
                        }
                    }
                }
            }
        }
        log.info("Oppdater mirror - Ferdig med å lagre $c hoved-/underenheter - $elapsed ms brukt")
    }

    private suspend inline fun <reified T> strømOgBlåsOpp(httpResponse: HttpResponse, block: (enhet: List<T>) -> Unit) {
        val contentLength = (httpResponse.contentLength() ?: -1)
        val contentLengthMB = contentLength / 1024 / 1024
        log.info("Komprimert filstørrelse: $contentLengthMB MiB ($contentLength bytes)")

        val gunzipStream = GZIPInputStream(httpResponse.bodyAsChannel().toInputStream())
        mapper.factory.createParser(gunzipStream).use { jsonParser ->
            // Check the first token
            check(jsonParser.nextToken() === JsonToken.START_ARRAY) { "Expected content to be an array" }

            // Iterate over the tokens until the end of the array
            val chunk = mutableListOf<T>()
            while (jsonParser.nextToken() !== JsonToken.END_ARRAY) {
                // Read an Organisasjonsenhet instance using ObjectMapper and do something with it
                val enhet: T = mapper.readValue(jsonParser)
                chunk.add(enhet)
                if (chunk.count() == 5000) {
                    block(chunk)
                }
            }
            if (chunk.isNotEmpty()) block(chunk)
        }
    }
}
