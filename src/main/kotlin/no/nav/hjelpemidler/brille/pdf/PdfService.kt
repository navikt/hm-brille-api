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

private val log = KotlinLogging.logger {}

class PdfService(
    private val props: Configuration.PdfProperties,
    private val client: HttpClient,
) {
    suspend fun genererPdf(behovsmelding: Any): ByteArray {
        return try {
            withContext(Dispatchers.IO) {
                client.post("${props.pdfgenUri}/api/v1/genpdf/hmb/hmb") {
                    contentType(Json)
                    setBody(behovsmelding)
                }.body()
            }
        } catch (e: Exception) {
            log.error(e) {
                "Feil ved generering av pdf"
            }
            throw e
        }
    }
}
