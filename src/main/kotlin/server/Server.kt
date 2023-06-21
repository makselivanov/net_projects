package server

import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlin.random.Random

const val SERVER = "127.0.0.1"
const val PORT = 8888
fun main() {
    val selectorManager = SelectorManager(Dispatchers.IO)
    val serverSocket = aSocket(selectorManager).udp().bind(InetSocketAddress(SERVER, PORT))
    while (true) {
        runBlocking {
            val response = serverSocket.receive()
            if (Random.nextFloat() > 0.2) { //send back
                val data = buildPacket {
                    val buffer = response.packet.copy()
                    append(buffer.readText().uppercase())
                }
                val datagram = Datagram(data, response.address)
                serverSocket.send(datagram)
            }
        }
    }
}