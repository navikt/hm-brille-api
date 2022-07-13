package no.nav.hjelpemidler.brille.redis

import no.nav.hjelpemidler.brille.Configuration
import no.nav.hjelpemidler.brille.enhetsregisteret.Organisasjonsenhet
import no.nav.hjelpemidler.brille.jsonMapper
import no.nav.hjelpemidler.brille.medlemskap.MedlemskapResultat
import org.apache.commons.pool2.impl.GenericObjectPoolConfig
import redis.clients.jedis.JedisPooled
import redis.clients.jedis.commands.StringCommands
import java.time.LocalDate

class RedisClient(private val redisProps: Configuration.RedisProperties = Configuration.redisProperties) {
    private val jedis: StringCommands = when (Configuration.profile) {
        Configuration.Profile.LOCAL -> JedisMock()
        else -> JedisPooled(
            GenericObjectPoolConfig(),
            redisProps.host,
            redisProps.port,
            2 * 1000, // 2 seconds
            redisProps.password
        )
    }

    fun erOptiker(fnr: String): Boolean? = jedis.get(erOptikerKey(fnr))?.toBoolean()

    fun setErOptiker(fnr: String, erOptiker: Boolean) {
        jedis.setex(erOptikerKey(fnr), redisProps.hprExpirySeconds, erOptiker.toString())
    }

    fun medlemskapBarn(fnr: String, bestillingsDato: LocalDate): MedlemskapResultat? =
        jedis.get(medlemskapBarnKey(fnr, bestillingsDato))?.let {
            jsonMapper.readValue(it, MedlemskapResultat::class.java)
        }

    fun setMedlemskapBarn(fnr: String, bestillingsDato: LocalDate, medlemskapResultat: MedlemskapResultat) {
        jedis.setex(
            medlemskapBarnKey(fnr, bestillingsDato),
            redisProps.medlemskapBarnExpirySeconds,
            jsonMapper.writeValueAsString(medlemskapResultat)
        )
    }

    fun organisasjonsenhet(orgnr: String): Organisasjonsenhet? = jedis.get(orgenhetKey(orgnr))?.let {
        jsonMapper.readValue(it, Organisasjonsenhet::class.java)
    }

    fun setOrganisasjonsenhet(orgnr: String, orgenhet: Organisasjonsenhet) {
        jedis.setex(
            orgenhetKey(orgnr),
            redisProps.orgenhetExpirySeconds,
            jsonMapper.writeValueAsString(orgenhet)
        )
    }
}

private fun erOptikerKey(fnr: String) = "fnr:$fnr:hpr:er.optiker"
private fun medlemskapBarnKey(fnr: String, bestillingsDato: LocalDate) =
    "fnr:$fnr:bestillingsDato:$bestillingsDato:medlemskapbarn:resultat"

private fun orgenhetKey(orgnr: String) = "orgnr:$orgnr:orgenhet"
