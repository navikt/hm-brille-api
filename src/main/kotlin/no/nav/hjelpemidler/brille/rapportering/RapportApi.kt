package no.nav.hjelpemidler.brille.rapportering

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.response.respondOutputStream
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import mu.KotlinLogging
import no.nav.hjelpemidler.brille.vedtak.Kravlinje
import java.io.OutputStream

private val log = KotlinLogging.logger { }

fun Route.rapportApi(rapportService: RapportService) {
    route("/kravlinjer") {
        get("/{orgnr}") {
            val orgnr = call.orgnr()
            val kravlinjer = rapportService.hentKravlinjer(orgnr)
            call.respond(HttpStatusCode.OK, kravlinjer)
        }

        get("/csv/{orgnr}") {
            val orgnr = call.orgnr()
            val kravlinjer = rapportService.hentKravlinjer(orgnr)
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
