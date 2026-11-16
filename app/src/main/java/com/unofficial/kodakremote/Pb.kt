package com.unofficial.kodakremote

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.EOFException
import java.io.InputStream

/** Tiny protobuf encoder/decoder, enough for the Android TV Remote v2 messages. */
object Pb {
    class Field(val num: Int, val wire: Int, val long: Long, val bytes: ByteArray?)

    fun varint(v: Long): ByteArray {
        val o = ByteArrayOutputStream()
        var x = v
        while (true) {
            if ((x and 0x7F.inv().toLong()) == 0L) {
                o.write(x.toInt())
                break
            }
            o.write(((x and 0x7F) or 0x80).toInt())
            x = x ushr 7
        }
        return o.toByteArray()
    }

    private fun tag(n: Int, wire: Int) = varint(((n shl 3) or wire).toLong())

    fun int(n: Int, v: Number): ByteArray = tag(n, 0) + varint(v.toLong())
    fun bytes(n: Int, b: ByteArray): ByteArray = tag(n, 2) + varint(b.size.toLong()) + b
    fun str(n: Int, s: String): ByteArray = bytes(n, s.toByteArray(Charsets.UTF_8))
    fun msg(n: Int, vararg parts: ByteArray): ByteArray {
        val o = ByteArrayOutputStream()
        parts.forEach { o.write(it) }
        return bytes(n, o.toByteArray())
    }

    fun readVarint(i: InputStream): Long {
        var shift = 0
        var result = 0L
        while (true) {
            val b = i.read()
            if (b < 0) throw EOFException("connection closed")
            result = result or ((b and 0x7F).toLong() shl shift)
            if (b and 0x80 == 0) return result
            shift += 7
            if (shift > 63) throw EOFException("bad varint")
        }
    }

    /** Reads one length-prefixed frame. */
    fun readFrame(i: InputStream): ByteArray {
        val len = readVarint(i).toInt()
        if (len < 0 || len > 1_000_000) throw EOFException("bad frame length")
        val buf = ByteArray(len)
        DataInputStream(i).readFully(buf)
        return buf
    }

    fun parse(b: ByteArray): List<Field> {
        val i = ByteArrayInputStream(b)
        val out = ArrayList<Field>()
        while (i.available() > 0) {
            val t = readVarint(i).toInt()
            val num = t ushr 3
            when (val wire = t and 7) {
                0 -> out.add(Field(num, wire, readVarint(i), null))
                2 -> {
                    val len = readVarint(i).toInt()
                    val buf = ByteArray(len)
                    DataInputStream(i).readFully(buf)
                    out.add(Field(num, wire, 0, buf))
                }
                1 -> { i.skip(8) }
                5 -> { i.skip(4) }
                else -> return out
            }
        }
        return out
    }
}
