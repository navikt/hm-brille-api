package no.nav.hjelpemidler.brille.pdf

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType.Application.Json
import io.ktor.http.contentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import no.nav.hjelpemidler.brille.Configuration

private val LOG = KotlinLogging.logger {}

class PdfService(
    private val httpClient: HttpClient,
    private val pdfProperties: Configuration.PdfProperties = Configuration.pdfProperties,
) {

    suspend fun genererPdf(behovsmelding: Any): ByteArray {
        return try {
            withContext(Dispatchers.IO) {
                httpClient.post("${pdfProperties.pdfgenUri}/api/v1/genpdf/hmb/hmb") {
                    contentType(Json)
                    setBody(behovsmelding)
                }.body()
            }
        } catch (e: Exception) {
            LOG.error("Feil ved generering av pdf", e)
            throw e
        }
    }
}
