package saw_protocol

import org.junit.jupiter.api.Test

import org.junit.jupiter.api.Assertions.*
import kotlin.random.Random

class StopAndWaitProtocolKtTest {

    @Test
    fun getControlSum() {
        val singularByte = byteArrayOf(0)
        getControlSum(singularByte) // should just work
        val zeroByte = byteArrayOf()
        getControlSum(zeroByte) // should just work
    }

    @Test
    fun checkCorruption() {
        val size = 10
        val data = Random.Default.nextBytes(size)
        val checkSum = getControlSum(data)
        assertFalse(checkCorruption(data, checkSum))
        for (i in 0 until size) {
            while (true) {
                val prevByte = data.get(i)
                val changedByte = Random.nextBytes(1)[0]
                if (prevByte != changedByte) {
                    data.set(i, changedByte)
                    assertTrue(checkCorruption(data, checkSum))
                    data.set(i, prevByte)
                    break
                }
            }
        }
    }
}