package no.nav.hjelpemidler.brille.test

interface Builder<out T> {
    fun build(): T
}
