package no.nav.hjelpemidler.brille

sealed class Either<out A, out B> {
    data class Left<A>(val value: A) : Either<A, Nothing>()
    data class Right<B>(val value: B) : Either<Nothing, B>()

    fun <C> map(fn: (B) -> C): Either<A, C> = flatMap { Right(fn(it)) }

    @Suppress("UNCHECKED_CAST")
    fun <A, C> flatMap(fn: (B) -> Either<A, C>): Either<A, C> = when (this) {
        is Left -> this as Either<A, C>
        is Right -> fn(value)
    }
}
