package no.nav.hjelpemidler.brille

import kotliquery.Row
import kotliquery.Session
import kotliquery.action.ExecuteQueryAction
import kotliquery.action.ListResultQueryAction
import kotliquery.action.NullableResultQueryAction
import kotliquery.action.UpdateAndReturnGeneratedKeyQueryAction
import kotliquery.action.UpdateQueryAction
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import org.intellij.lang.annotations.Language
import javax.sql.DataSource

private fun <T> DataSource.usingSession(block: (Session) -> T) = using(sessionOf(this), block)

private fun <T> DataSource.runAction(action: NullableResultQueryAction<T>): T? = usingSession {
    it.run(action)
}

private fun <T> DataSource.runAction(action: ListResultQueryAction<T>): List<T> = usingSession {
    it.run(action)
}

private fun DataSource.runAction(action: UpdateQueryAction): Int = usingSession {
    it.run(action)
}

private fun DataSource.runAction(action: UpdateAndReturnGeneratedKeyQueryAction): Long? = usingSession {
    it.run(action)
}

private fun DataSource.runAction(action: ExecuteQueryAction): Boolean = usingSession {
    it.run(action)
}

typealias QueryParameters = Map<String, Any?>
typealias ResultMapper<T> = (Row) -> T?

fun <T> DataSource.query(
    @Language("PostgreSQL") sql: String,
    queryParameters: QueryParameters = emptyMap(),
    mapper: ResultMapper<T>,
): T? = runAction(queryOf(sql, queryParameters).map(mapper).asSingle)

fun <T> DataSource.queryList(
    @Language("PostgreSQL") sql: String,
    queryParameters: QueryParameters = emptyMap(),
    mapper: ResultMapper<T>,
): List<T> = runAction(queryOf(sql, queryParameters).map(mapper).asList)

fun DataSource.update(
    @Language("PostgreSQL") sql: String,
    queryParameters: QueryParameters = emptyMap(),
) = runAction(queryOf(sql, queryParameters).asUpdate)

fun DataSource.updateAndReturnGeneratedKey(
    @Language("PostgreSQL") sql: String,
    queryParameters: QueryParameters = emptyMap(),
) = runAction(queryOf(sql, queryParameters).asUpdateAndReturnGeneratedKey)

fun DataSource.execute(
    @Language("PostgreSQL") sql: String,
    queryParameters: QueryParameters = emptyMap(),
) = runAction(queryOf(sql, queryParameters).asExecute)
