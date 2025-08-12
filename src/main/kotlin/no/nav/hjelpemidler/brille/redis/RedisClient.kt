package no.nav.hjelpemidler.brille.redis

import no.nav.hjelpemidler.brille.Configuration
import no.nav.hjelpemidler.brille.enhetsregisteret.Organisasjonsenhet
import no.nav.hjelpemidler.brille.medlemskap.MedlemskapResultat
import no.nav.hjelpemidler.configuration.Environment
import no.nav.hjelpemidler.serialization.jackson.jsonMapper
import org.apache.commons.pool2.impl.GenericObjectPoolConfig
import redis.clients.jedis.JedisPooled
import redis.clients.jedis.commands.StringCommands
import java.time.LocalDate

class RedisClient(private val redisProps: Configuration.RedisProperties = Configuration.redisProperties) {
    private val jedis: StringCommands = if (Environment.current.isLocal) {
        JedisMock()
    } else {
        JedisPooled(
            GenericObjectPoolConfig(),
            redisProps.host,
            redisProps.port,
            2 * 1000, // 2 seconds
            redisProps.username,
            redisProps.password,
        )
    }

    fun erOptiker(fnr: String): Boolean? = jedis.get(erOptikerKey(fnr))?.toBoolean()

    fun setErOptiker(fnr: String, erOptiker: Boolean) {
        jedis.setex(erOptikerKey(fnr), redisProps.hprExpirySeconds, erOptiker.toString())
    }

    fun optikerNavn(fnr: String): String? = jedis.get(optikerNavnKey(fnr))

    fun setOptikerNavn(fnr: String, optikerNavn: String) {
        jedis.setex(optikerNavnKey(fnr), redisProps.hprExpirySeconds, optikerNavn)
    }

    fun medlemskapBarn(fnr: String, bestillingsdato: LocalDate): MedlemskapResultat? =
        jedis.get(medlemskapBarnKey(fnr, bestillingsdato))?.let {
            jsonMapper.readValue(it, MedlemskapResultat::class.java)
        }

    fun setMedlemskapBarn(fnr: String, bestillingsdato: LocalDate, medlemskapResultat: MedlemskapResultat) {
        jedis.setex(
            medlemskapBarnKey(fnr, bestillingsdato),
            redisProps.medlemskapBarnExpirySeconds(),
            jsonMapper.writeValueAsString(medlemskapResultat),
        )
    }

    fun organisasjonsenhet(orgnr: String): Organisasjonsenhet? = jedis.get(orgenhetKey(orgnr))?.let {
        jsonMapper.readValue(it, Organisasjonsenhet::class.java)
    }

    fun setOrganisasjonsenhet(orgnr: String, orgenhet: Organisasjonsenhet) {
        jedis.setex(
            orgenhetKey(orgnr),
            redisProps.orgenhetExpirySeconds,
            jsonMapper.writeValueAsString(orgenhet),
        )
    }
}

private fun erOptikerKey(fnr: String) = "fnr:$fnr:hpr:er.optiker"
private fun optikerNavnKey(fnr: String) = "fnr:$fnr:hpr:optiker.navn"
private fun medlemskapBarnKey(fnr: String, bestillingsdato: LocalDate) =
    "fnr:$fnr:bestillingsdato:$bestillingsdato:medlemskapbarn:resultat"

private fun orgenhetKey(orgnr: String) = "orgnr:$orgnr:orgenhet"
