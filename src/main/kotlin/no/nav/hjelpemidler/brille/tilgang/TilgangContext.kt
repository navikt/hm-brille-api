package no.nav.hjelpemidler.brille.tilgang

import io.ktor.server.application.ApplicationCall
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

class TilgangContextElement(
    val currentUser: UserPrincipal,
) : AbstractCoroutineContextElement(TilgangContextElement) {
    companion object Key : CoroutineContext.Key<TilgangContextElement>
}

suspend fun <T> withTilgangContext(
    context: TilgangContextElement,
    block: suspend CoroutineScope.() -> T,
): T =
    withContext(context, block)

suspend fun <T> withTilgangContext(
    call: ApplicationCall,
    block: suspend CoroutineScope.() -> T,
): T =
    withTilgangContext(TilgangContextElement(call.currentUser()), block)

fun CoroutineContext.tilgangContext(): TilgangContextElement =
    this[TilgangContextElement.Key] ?: TilgangContextElement(UserPrincipal.Ingen)

suspend fun tilgangContext(): TilgangContextElement =
    currentCoroutineContext().tilgangContext()

suspend fun currentUser(): UserPrincipal =
    tilgangContext().currentUser
