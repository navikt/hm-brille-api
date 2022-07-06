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

private fun <T> DataSource.session(block: (Session) -> T) = using(sessionOf(this), block)

private fun <T> DataSource.action(action: NullableResultQueryAction<T>): T? = session { it.run(action) }
private fun <T> DataSource.action(action: ListResultQueryAction<T>): List<T> = session { it.run(action) }
private fun DataSource.action(action: UpdateQueryAction): Int = session { it.run(action) }
private fun DataSource.action(action: UpdateAndReturnGeneratedKeyQueryAction): Long? = session { it.run(action) }
private fun DataSource.action(action: ExecuteQueryAction): Boolean = session { it.run(action) }

fun <T> DataSource.query(
    @Language("PostgreSQL") sql: String,
    params: Map<String, Any?> = emptyMap(),
    mapper: (Row) -> T?,
): T? = action(queryOf(sql, params).map(mapper).asSingle)

fun <T> DataSource.queryList(
    @Language("PostgreSQL") sql: String,
    params: Map<String, Any?> = emptyMap(),
    mapper: (Row) -> T?,
): List<T> = action(queryOf(sql, params).map(mapper).asList)

fun DataSource.update(
    @Language("PostgreSQL") sql: String,
    params: Map<String, Any?> = emptyMap(),
) = action(queryOf(sql, params).asUpdate)

fun DataSource.updateAndReturnGeneratedKey(
    @Language("PostgreSQL") sql: String,
    params: Map<String, Any?> = emptyMap(),
) = action(queryOf(sql, params).asUpdateAndReturnGeneratedKey)

fun DataSource.execute(
    @Language("PostgreSQL") sql: String,
    params: Map<String, Any?> = emptyMap(),
) = action(queryOf(sql, params).asExecute)
