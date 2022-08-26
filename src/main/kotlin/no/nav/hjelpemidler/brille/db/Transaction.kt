package no.nav.hjelpemidler.brille.db

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotliquery.sessionOf
import kotliquery.using
import no.nav.hjelpemidler.saksbehandling.db.TransactionalSessionFactory

suspend fun <R> transaction(
    context: DatabaseContext,
    block: (DatabaseSessionContext) -> R,
) =
    withContext(Dispatchers.IO) {
        using(sessionOf(context.dataSource)) { session ->
            session.transaction { tx ->
                block(context.createSessionContext(TransactionalSessionFactory(tx)))
            }
        }
    }
