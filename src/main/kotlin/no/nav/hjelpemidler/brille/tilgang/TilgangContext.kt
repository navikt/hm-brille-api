package no.nav.hjelpemidler.brille.tilgang

import io.ktor.server.application.ApplicationCall
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

class TilgangContextElement(
    val innloggetBruker: InnloggetBruker,
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
    withTilgangContext(TilgangContextElement(call.innloggetBruker()), block)

fun CoroutineContext.tilgangContext(): TilgangContextElement =
    this[TilgangContextElement.Key] ?: TilgangContextElement(InnloggetBruker.Ingen)

suspend fun tilgangContext(): TilgangContextElement =
    currentCoroutineContext().tilgangContext()

suspend fun innloggetBruker(): InnloggetBruker =
    tilgangContext().innloggetBruker
