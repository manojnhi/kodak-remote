package com.unofficial.kodakremote

import android.content.Context
import android.hardware.ConsumerIrManager

/** Infrared output through the phone's IR blaster (ConsumerIrManager). Nothing is sent over the network. */
class IrController(context: Context) {
    private val manager = context.getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager

    val available: Boolean get() = manager?.hasIrEmitter() == true

    /** NEC protocol, 38 kHz. addr > 0xFF is treated as a 16-bit (extended) address. */
    fun send(address: Int, command: Int): Boolean {
        val m = manager ?: return false
        return try {
            m.transmit(38000, necPattern(address, command))
            true
        } catch (e: Exception) {
            false
        }
    }

    companion object {
        fun necPattern(address: Int, command: Int): IntArray {
            val a = address and 0xFFFF
            val c = command and 0xFF
            val cInv = c.inv() and 0xFF
            val word: Long = if (a > 0xFF) {
                a.toLong() or (c.toLong() shl 16) or (cInv.toLong() shl 24)
            } else {
                val aInv = a.inv() and 0xFF
                a.toLong() or (aInv.toLong() shl 8) or (c.toLong() shl 16) or (cInv.toLong() shl 24)
            }
            val p = ArrayList<Int>(70)
            p.add(9000); p.add(4500)
            for (i in 0 until 32) {
                p.add(560)
                p.add(if (((word shr i) and 1L) == 1L) 1690 else 560)
            }
            p.add(560)
            return p.toIntArray()
        }
    }
}
