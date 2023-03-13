package no.nav.hjelpemidler.brille.tilgang

import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.principal

fun ApplicationCall.currentUser(): UserPrincipal =
    principal() ?: UserPrincipal.Ingen
