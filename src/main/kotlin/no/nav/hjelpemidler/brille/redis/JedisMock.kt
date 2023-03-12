package no.nav.hjelpemidler.brille.redis

import redis.clients.jedis.args.BitCountOption
import redis.clients.jedis.args.BitOP
import redis.clients.jedis.commands.StringCommands
import redis.clients.jedis.params.BitPosParams
import redis.clients.jedis.params.GetExParams
import redis.clients.jedis.params.LCSParams
import redis.clients.jedis.params.SetParams
import redis.clients.jedis.params.StrAlgoLCSParams
import redis.clients.jedis.resps.LCSMatchResult

class JedisMock : StringCommands {

    private val store = mutableMapOf<String, String>()

    override fun get(key: String): String? {
        return store[key]
    }

    override fun setGet(key: String?, value: String?, params: SetParams?): String {
        TODO("Not yet implemented")
    }

    override fun setex(key: String, seconds: Long, value: String): String? {
        store[key] = value
        return "OK"
    }

    override fun set(key: String?, value: String?): String {
        TODO("Not yet implemented")
    }

    override fun set(key: String?, value: String?, params: SetParams?): String {
        TODO("Not yet implemented")
    }

    override fun getDel(key: String?): String {
        TODO("Not yet implemented")
    }

    override fun getEx(key: String?, params: GetExParams?): String {
        TODO("Not yet implemented")
    }

    override fun setbit(key: String?, offset: Long, value: Boolean): Boolean {
        TODO("Not yet implemented")
    }

    override fun getbit(key: String?, offset: Long): Boolean {
        TODO("Not yet implemented")
    }

    override fun setrange(key: String?, offset: Long, value: String?): Long {
        TODO("Not yet implemented")
    }

    override fun getrange(key: String?, startOffset: Long, endOffset: Long): String {
        TODO("Not yet implemented")
    }

    override fun getSet(key: String?, value: String?): String {
        TODO("Not yet implemented")
    }

    override fun setnx(key: String?, value: String?): Long {
        TODO("Not yet implemented")
    }

    override fun psetex(key: String?, milliseconds: Long, value: String?): String {
        TODO("Not yet implemented")
    }

    override fun mget(vararg keys: String?): MutableList<String> {
        TODO("Not yet implemented")
    }

    override fun mset(vararg keysvalues: String?): String {
        TODO("Not yet implemented")
    }

    override fun msetnx(vararg keysvalues: String?): Long {
        TODO("Not yet implemented")
    }

    override fun incr(key: String?): Long {
        TODO("Not yet implemented")
    }

    override fun incrBy(key: String?, increment: Long): Long {
        TODO("Not yet implemented")
    }

    override fun incrByFloat(key: String?, increment: Double): Double {
        TODO("Not yet implemented")
    }

    override fun decr(key: String?): Long {
        TODO("Not yet implemented")
    }

    override fun decrBy(key: String?, decrement: Long): Long {
        TODO("Not yet implemented")
    }

    override fun append(key: String?, value: String?): Long {
        TODO("Not yet implemented")
    }

    override fun substr(key: String?, start: Int, end: Int): String {
        TODO("Not yet implemented")
    }

    override fun strlen(key: String?): Long {
        TODO("Not yet implemented")
    }

    override fun bitcount(key: String?): Long {
        TODO("Not yet implemented")
    }

    override fun bitcount(key: String?, start: Long, end: Long): Long {
        TODO("Not yet implemented")
    }

    override fun bitcount(key: String?, start: Long, end: Long, option: BitCountOption?): Long {
        TODO("Not yet implemented")
    }

    override fun bitpos(key: String?, value: Boolean): Long {
        TODO("Not yet implemented")
    }

    override fun bitpos(key: String?, value: Boolean, params: BitPosParams?): Long {
        TODO("Not yet implemented")
    }

    override fun bitfield(key: String?, vararg arguments: String?): MutableList<Long> {
        TODO("Not yet implemented")
    }

    override fun bitfieldReadonly(key: String?, vararg arguments: String?): MutableList<Long> {
        TODO("Not yet implemented")
    }

    override fun bitop(op: BitOP?, destKey: String?, vararg srcKeys: String?): Long {
        TODO("Not yet implemented")
    }

    override fun strAlgoLCSKeys(keyA: String?, keyB: String?, params: StrAlgoLCSParams?): LCSMatchResult {
        TODO("Not yet implemented")
    }

    override fun lcs(keyA: String?, keyB: String?, params: LCSParams?): LCSMatchResult {
        TODO("Not yet implemented")
    }
}
