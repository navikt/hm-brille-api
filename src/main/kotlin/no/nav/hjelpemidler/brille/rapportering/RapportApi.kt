package no.nav.hjelpemidler.brille.rapportering

import io.ktor.http.ContentDisposition
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondOutputStream
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import mu.KotlinLogging
import no.nav.hjelpemidler.brille.altinn.AltinnService
import no.nav.hjelpemidler.brille.extractFnr
import no.nav.hjelpemidler.brille.vedtak.Kravlinje
import java.io.OutputStream
import java.util.Date

private val log = KotlinLogging.logger { }

fun Route.rapportApi(rapportService: RapportService, altinnService: AltinnService) {
    route("/kravlinjer") {
        get("/{orgnr}") {
            val orgnr = call.orgnr()
            if (!altinnService.erHovedadministratorFor(call.extractFnr(), orgnr)) {
                call.respond(HttpStatusCode.Unauthorized)
            }
            val kravlinjer = rapportService.hentKravlinjer(orgnr)
            call.respond(HttpStatusCode.OK, kravlinjer)
        }

        get("/paged/{orgnr}") {
            val orgnr = call.orgnr()
            if (!altinnService.erHovedadministratorFor(call.extractFnr(), orgnr)) {
                call.respond(HttpStatusCode.Unauthorized)
            }
            val limit = call.request.queryParameters["limit"]?.toInt() ?: 20
            val page = call.request.queryParameters["page"]?.toInt() ?: 1
            val pagedKravlinjer = rapportService.hentPagedKravlinjer(
                orgNr = orgnr,
                limit = limit,
                offset = (page - 1) * limit,
            )
            call.respond(HttpStatusCode.OK, pagedKravlinjer)
        }

        get("/csv/{orgnr}") {
            val orgnr = call.orgnr()
            if (!altinnService.erHovedadministratorFor(call.extractFnr(), orgnr)) {
                call.respond(HttpStatusCode.Unauthorized)
            }
            val kravlinjer = rapportService.hentKravlinjer(orgnr)
            call.response.header(
                HttpHeaders.ContentDisposition,
                ContentDisposition.Attachment.withParameter(
                    ContentDisposition.Parameters.FileName,
                    "${Date().time}.csv"
                )
                    .toString()
            )
            call.respondOutputStream(
                status = HttpStatusCode.OK,
                contentType = ContentType.Text.CSV,
                producer = producer(kravlinjer)
            )
        }
    }
}

fun producer(kravlinjer: List<Kravlinje>): suspend OutputStream.() -> Unit = {
    write("NAV referanse, Deres referanse, Kravbeløp, Opprettet dato, Utbetalt".toByteArray())
    write("\n".toByteArray())
    kravlinjer.forEach {
        write("${it.id}, ${it.bestillingsreferanse}, ${it.beløp}, ${it.bestillingsdato}, Nei".toByteArray())
        write("\n".toByteArray())
    }
}

private fun ApplicationCall.orgnr(): String = requireNotNull(parameters["orgnr"]) {
    "Mangler orgnr i URL"
}
