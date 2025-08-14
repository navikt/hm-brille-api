package no.nav.hjelpemidler.brille.redis

import io.valkey.args.BitCountOption
import io.valkey.args.BitOP
import io.valkey.commands.StringCommands
import io.valkey.params.BitPosParams
import io.valkey.params.GetExParams
import io.valkey.params.LCSParams
import io.valkey.params.SetParams
import io.valkey.resps.LCSMatchResult

class ValkeyPoolExtension(private val pool: io.valkey.JedisPool) : StringCommands {
    override fun append(key: String, value: String): Long =
        pool.resource.use { it.append(key, value) }

    override fun decr(key: String): Long =
        pool.resource.use { it.decr(key) }

    override fun decrBy(key: String, decrement: Long): Long =
        pool.resource.use { it.decrBy(key, decrement) }

    override fun get(key: String): String? =
        pool.resource.use { it.get(key) }

    override fun getDel(key: String): String? =
        pool.resource.use { it.getDel(key) }

    override fun getEx(key: String, params: GetExParams): String? =
        pool.resource.use { it.getEx(key, params) }

    override fun getrange(key: String, startOffset: Long, endOffset: Long): String? =
        pool.resource.use { it.getrange(key, startOffset, endOffset) }

    @Suppress("DEPRECATION")
    @Deprecated("Deprecated in Java")
    override fun getSet(key: String, value: String): String? =
        pool.resource.use { it.getSet(key, value) }

    override fun incr(key: String): Long =
        pool.resource.use { it.incr(key) }

    override fun incrBy(key: String, increment: Long): Long =
        pool.resource.use { it.incrBy(key, increment) }

    override fun incrByFloat(key: String, increment: Double): Double =
        pool.resource.use { it.incrByFloat(key, increment) }

    override fun lcs(keyA: String?, keyB: String?, params: LCSParams?): LCSMatchResult? =
        pool.resource.use { it.lcs(keyA, keyB, params) }

    override fun mget(vararg keys: String): List<String?> =
        pool.resource.use { it.mget(*keys) }

    override fun mset(vararg keysvalues: String): String? =
        pool.resource.use { it.mset(*keysvalues) }

    override fun msetnx(vararg keysvalues: String): Long =
        pool.resource.use { it.msetnx(*keysvalues) }

    override fun psetex(key: String, milliseconds: Long, value: String): String? =
        pool.resource.use { it.psetex(key, milliseconds, value) }

    override fun set(key: String, value: String): String? =
        pool.resource.use { it.set(key, value) }

    override fun set(key: String, value: String, params: SetParams): String? =
        pool.resource.use { it.set(key, value, params) }

    override fun setGet(key: String, value: String): String? =
        pool.resource.use { it.setGet(key, value) }

    override fun setGet(key: String, value: String, params: SetParams): String? =
        pool.resource.use { it.setGet(key, value, params) }

    override fun setex(key: String, seconds: Long, value: String): String? =
        pool.resource.use { it.setex(key, seconds, value) }

    override fun setnx(key: String, value: String): Long =
        pool.resource.use { it.setnx(key, value) }

    override fun setrange(key: String, offset: Long, value: String): Long =
        pool.resource.use { it.setrange(key, offset, value) }

    override fun strlen(key: String): Long =
        pool.resource.use { it.strlen(key) }

    override fun substr(key: String, start: Int, end: Int): String? =
        pool.resource.use { it.substr(key, start, end) }

    override fun setIfeq(key: String?, value: String?, compareValue: String?): String? {
        TODO("Not yet implemented")
    }

    override fun delIfeq(key: String?, compareValue: String?): Boolean? {
        TODO("Not yet implemented")
    }

    override fun setbit(key: String?, offset: Long, value: Boolean): Boolean {
        TODO("Not yet implemented")
    }

    override fun getbit(key: String?, offset: Long): Boolean {
        TODO("Not yet implemented")
    }

    override fun bitcount(key: String?): Long {
        TODO("Not yet implemented")
    }

    override fun bitcount(key: String?, start: Long, end: Long): Long {
        TODO("Not yet implemented")
    }

    override fun bitcount(
        key: String?,
        start: Long,
        end: Long,
        option: BitCountOption?,
    ): Long {
        TODO("Not yet implemented")
    }

    override fun bitpos(key: String?, value: Boolean): Long {
        TODO("Not yet implemented")
    }

    override fun bitpos(
        key: String?,
        value: Boolean,
        params: BitPosParams?,
    ): Long {
        TODO("Not yet implemented")
    }

    override fun bitfield(
        key: String?,
        vararg arguments: String?,
    ): List<Long?>? {
        TODO("Not yet implemented")
    }

    override fun bitfieldReadonly(
        key: String?,
        vararg arguments: String?,
    ): List<Long?>? {
        TODO("Not yet implemented")
    }

    override fun bitop(
        op: BitOP?,
        destKey: String?,
        vararg srcKeys: String?,
    ): Long {
        TODO("Not yet implemented")
    }
}
