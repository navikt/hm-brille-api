package no.nav.hjelpemidler.brille.vilkarsvurdering

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import no.nav.hjelpemidler.brille.Configuration
import no.nav.hjelpemidler.brille.audit.AuditService
import no.nav.hjelpemidler.brille.extractFnr

fun Route.vilkårApi(vilkårsvurderingService: VilkårsvurderingService, auditService: AuditService) {
    post("/vilkarsgrunnlag") {
        if (Configuration.prod) { // TODO: fjern før prodsetting
            call.respond(HttpStatusCode.Unauthorized)
            return@post
        }

        val vilkårsgrunnlag = call.receive<VilkårsgrunnlagDto>()
        auditService.lagreOppslag(
            fnrInnlogget = call.extractFnr(),
            fnrOppslag = vilkårsgrunnlag.fnrBruker,
            oppslagBeskrivelse = "[POST] /vilkarsgrunnlag - Sjekk om innbygger og bestilling oppfyller vilkår for støtte"
        )
        val vilkarsvurdering = vilkårsvurderingService.vurderVilkårBrille(vilkårsgrunnlag)

        call.respond(VilkårsvurderingDto(vilkarsvurdering.utfall))
    }
}
