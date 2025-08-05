package no.nav.hjelpemidler.brille.test

import io.ktor.client.request.HttpRequestBuilder
import no.nav.hjelpemidler.http.openid.TokenSet
import no.nav.hjelpemidler.http.openid.TokenSetProvider
import kotlin.time.Duration.Companion.hours

class TestTokenSetProvider(private val tokenSet: TokenSet = TokenSet("token", 1.hours)) : TokenSetProvider {
    override suspend fun invoke(request: HttpRequestBuilder) = tokenSet
}
