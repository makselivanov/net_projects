package client

import org.slf4j.LoggerFactory
import saw_protocol.StopAndWaitProtocol
import saw_protocol.serverPort
import java.io.File
import kotlin.time.Duration.Companion.seconds

private val logger = LoggerFactory.getLogger("Local client")

fun main() {
    val protocolManager = StopAndWaitProtocol("client")
    protocolManager.bind()
    protocolManager.connect("127.0.0.1", serverPort)
    val data = File("alice.txt").readBytes()
    protocolManager.setBufferSize(2048)
    protocolManager.setTimer(0.5.seconds)
    protocolManager.send(data, true)
    logger.info("Sent file to server")

    //We sent data, lets get it back from server
    val (_, receive) = protocolManager.receive()
    //Let's check that data is equal
    if (receive.contentEquals(data)) {
        println("Great, sent and received data is equal!")
    } else {
        println("Oh no, something went wrong, sent and received data is not equal...")
    }
    protocolManager.disconnect()
}