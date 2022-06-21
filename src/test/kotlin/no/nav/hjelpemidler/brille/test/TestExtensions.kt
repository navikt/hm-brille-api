package no.nav.hjelpemidler.brille.test

import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

infix fun <T> T.shouldBe(expected: T) = assertEquals(expected, this)
infix fun <T> T.shouldNotBe(illegal: T) = assertNotEquals(illegal, this)
