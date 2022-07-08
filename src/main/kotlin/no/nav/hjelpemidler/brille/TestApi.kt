package no.nav.hjelpemidler.brille

import io.ktor.server.routing.Route
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
    }
}
