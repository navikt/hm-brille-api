package no.nav.hjelpemidler.brille.vilkarsvurdering

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import no.nav.hjelpemidler.brille.Configuration
import no.nav.hjelpemidler.brille.Profile
import no.nav.hjelpemidler.brille.nare.evaluering.Resultat

fun Route.vilkårApi(vilkårsvurderingService: VilkårsvurderingService) {
    post("/vilkarsgrunnlag") {
        if (Configuration.profile == Profile.PROD) { // TODO: fjern før prodsetting
            call.respond(HttpStatusCode.Unauthorized)
            return@post
        }

        val vilkårsgrunnlagDto = call.receive<VilkårsgrunnlagDto>()

        val evaluering = vilkårsvurderingService.vurderVilkår(vilkårsgrunnlagDto)

        call.respond(VilkårsvurderingResultatDto(evaluering.resultat))
    }
}

data class VilkårsvurderingResultatDto(
    val resultat: Resultat
)
