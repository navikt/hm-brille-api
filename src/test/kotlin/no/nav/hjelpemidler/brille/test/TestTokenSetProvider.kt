package no.nav.hjelpemidler.brille.test

import no.nav.hjelpemidler.http.openid.TokenSet
import no.nav.hjelpemidler.http.openid.TokenSetProvider
import kotlin.time.Duration.Companion.hours

class TestTokenSetProvider(private val tokenSet: TokenSet = TokenSet.bearer(1.hours, "token")) : TokenSetProvider {
    override suspend fun invoke(): TokenSet = tokenSet
}
