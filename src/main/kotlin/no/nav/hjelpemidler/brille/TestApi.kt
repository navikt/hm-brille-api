package no.nav.hjelpemidler.brille

import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import mu.KotlinLogging
import no.nav.hjelpemidler.brille.enhetsregisteret.EnhetsregisteretService
import no.nav.hjelpemidler.brille.medlemskap.MedlemskapBarn
import no.nav.hjelpemidler.brille.medlemskap.MedlemskapClient
import no.nav.hjelpemidler.brille.virksomhet.VirksomhetStore

private val log = KotlinLogging.logger { }

// FIXME: Remove eventually
@Deprecated("fjernes")
fun Route.testApi(
    medlemskapClient: MedlemskapClient,
    medlemskapBarn: MedlemskapBarn,
    virksomhetStore: VirksomhetStore,
    enhetsregisteretService: EnhetsregisteretService,
) {
    route("/test") {
        post("/medlemskap-client") {
            data class Request(val fnr: String)

            val fnr = call.receive<Request>().fnr
            call.respond(medlemskapClient.slåOppMedlemskap(fnr))
        }

        post("/medlemskap-barn") {
            data class Request(val fnr: String)

            val fnr = call.receive<Request>().fnr
            call.respond(medlemskapBarn.sjekkMedlemskapBarn(fnr))
        }
    }
}
