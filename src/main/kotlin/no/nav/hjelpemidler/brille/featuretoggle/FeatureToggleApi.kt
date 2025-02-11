package no.nav.hjelpemidler.brille.featuretoggle

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route

private val log = KotlinLogging.logger { }

fun Route.featureToggleApi(featureToggleService: FeatureToggleService) {
    route("/features") {
        get {
            val features = call.request.queryParameters.getAll("feature")

            call.respond(HttpStatusCode.OK, featureToggleService.hentFeatureToggles(features))
        }
    }
}
