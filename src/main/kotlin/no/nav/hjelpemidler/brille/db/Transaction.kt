package no.nav.hjelpemidler.brille.db

suspend fun <R> transaction(
    context: DatabaseContext,
    block: suspend (DatabaseTransactionContext) -> R,
): R = context(block)
