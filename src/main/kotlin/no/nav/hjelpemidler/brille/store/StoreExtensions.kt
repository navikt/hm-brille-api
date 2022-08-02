package no.nav.hjelpemidler.brille.store

import kotliquery.Query
import kotliquery.Row
import kotliquery.Session
import kotliquery.action.ExecuteQueryAction
import kotliquery.action.ListResultQueryAction
import kotliquery.action.NullableResultQueryAction
import kotliquery.action.QueryAction
import kotliquery.action.ResultQueryActionBuilder
import kotliquery.action.UpdateAndReturnGeneratedKeyQueryAction
import kotliquery.action.UpdateQueryAction
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import org.intellij.lang.annotations.Language
import javax.sql.DataSource

const val COLUMN_LABEL_TOTAL = "total"

private fun <T> DataSource.usingSession(block: (Session) -> T) = using(sessionOf(this), block)

private fun <T> DataSource.runAction(action: NullableResultQueryAction<T>): T? = usingSession {
    it.run(action)
}

private fun <T> DataSource.runAction(action: ListResultQueryAction<T>): List<T> = usingSession {
    it.run(action)
}

private fun <T> DataSource.runAction(action: PageResultQueryAction<T>): Page<T> = usingSession {
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

data class UpdateResult(
    val rowCount: Int? = null,
    val generatedId: Long? = null,
) {
    fun validate() {
        if (rowCount == 0) {
            throw StoreException("rowsUpdated var 0")
        }
    }
}

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

fun <T> DataSource.queryList(
    @Language("PostgreSQL") sql: String,
    queryParameters: List<String> = emptyList(),
    mapper: ResultMapper<T>,
): List<T> = runAction(queryOf(sql, params = queryParameters.toTypedArray()).map(mapper).asList)

fun <T> DataSource.queryPagedList(
    @Language("PostgreSQL") sql: String,
    queryParameters: QueryParameters = emptyMap(),
    limit: Int,
    offset: Int,
    mapper: ResultMapper<T>,
): Page<T> = runAction(queryOf(sql, queryParameters).map(mapper).asPage(limit, offset))

fun DataSource.update(
    @Language("PostgreSQL") sql: String,
    queryParameters: QueryParameters = emptyMap(),
): UpdateResult = UpdateResult(rowCount = runAction(queryOf(sql, queryParameters).asUpdate))

fun DataSource.updateAndReturnGeneratedKey(
    @Language("PostgreSQL") sql: String,
    queryParameters: QueryParameters = emptyMap(),
): UpdateResult = UpdateResult(generatedId = runAction(queryOf(sql, queryParameters).asUpdateAndReturnGeneratedKey))

fun DataSource.execute(
    @Language("PostgreSQL") sql: String,
    queryParameters: QueryParameters = emptyMap(),
): Boolean = runAction(queryOf(sql, queryParameters).asExecute)

data class Page<T>(
    val items: List<T>,
    val total: Int,
) : List<T> by items

data class PageResultQueryAction<A>(
    val query: Query,
    val extractor: (Row) -> A?,
    val limit: Int,
    val offset: Int,
) : QueryAction<Page<A>> {
    override fun runWithSession(session: Session): Page<A> {
        var totalNumberOfItems = -1
        val items = session.list(
            query.let {
                Query(
                    "${it.statement} limit :limit offset :offset",
                    it.params,
                    it.paramMap.plus(
                        mapOf(
                            "limit" to limit + 1, // fetch one more than limit to check for "hasMore"
                            "offset" to offset,
                        )
                    )
                )
            }
        ) {
            totalNumberOfItems = it.intOrNull(COLUMN_LABEL_TOTAL) ?: -1
            extractor(it)
        }
        return Page(
            items = items.take(limit),
            total = totalNumberOfItems,
        )
    }
}

fun <A> ResultQueryActionBuilder<A>.asPage(limit: Int, offset: Int): PageResultQueryAction<A> =
    PageResultQueryAction(query, extractor, limit, offset)

fun <A> Session.run(action: PageResultQueryAction<A>): Page<A> = action.runWithSession(this)
