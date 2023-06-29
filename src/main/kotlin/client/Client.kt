package client

import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.*
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.properties.Delegates
import kotlin.system.measureNanoTime
import kotlin.system.measureTimeMillis
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds

const val SERVER = "0:0:0:0:0:0:0:0"
const val PORT = 8888
const val N = 10
const val LEN = 40
val TIMER = 1.seconds

fun nanoToMilliDecimal(nano: Long) : Double {
    return (nano.nanoseconds.inWholeMicroseconds / 100) * 1.0 / 10
}

fun main() {
    val selectorManager = SelectorManager(Dispatchers.IO)
    val clientSocket = aSocket(selectorManager).udp().bind()
    val address = InetSocketAddress(SERVER, PORT)
    val experiments: MutableList<Double> = mutableListOf()
    val formatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")
    for (i in 0 until N) {
        val str = "Ping ${i+1} ${LocalDateTime.now().format(formatter)}"
        val data = buildPacket {
            append(str)
        }
        val datagram = Datagram(data, address)
        var timeInNanos by Delegates.notNull<Long>()
        var response: ByteReadPacket? = null
        runBlocking {
            clientSocket.send(datagram)
            timeInNanos = measureNanoTime {
                withTimeoutOrNull(TIMER) {
                    response = clientSocket.receive().packet
                }
            }
        }
        experiments.add(nanoToMilliDecimal(timeInNanos))
        println("Sending : $str")
        if (response == null) {
            println("Request timed out")
        } else {
            println("Response: ${response!!.readText()}")
        }

        println("RTT is ${nanoToMilliDecimal(timeInNanos)} ms")
    }
    println("""
        Average time is ${experiments.average()} ms
    """.trimIndent())
}