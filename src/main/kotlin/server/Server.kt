package server

import io.ktor.network.sockets.*
import org.slf4j.LoggerFactory
import saw_protocol.*
import java.io.File
import kotlin.text.String

private val logger = LoggerFactory.getLogger("Local server")
fun main() {
    val protocolManager = StopAndWaitProtocol("server")
    protocolManager.bind(port = serverPort)
    var returnAddress: InetSocketAddress? = null
    while (true) {
        val (type, data) = protocolManager.receive()
        when (type) {
            TypeData.HELLO -> {
                val buffer = String(data).split(":")
                val port = buffer[buffer.size - 1].toInt()
                val hostname = buffer.subList(0, buffer.size - 1).joinToString(":")
                returnAddress = InetSocketAddress(hostname, port)
                logger.info("Received Hello message from: $returnAddress")
            }
            TypeData.DATA -> {
                //We get file, lets save it and sent it back with max datagrams
                logger.info("Received Data with size ${data.size} bytes")
                File("serverBackup.txt").writeBytes(data)
                protocolManager.setBufferSize(65507  - protocolManager.headerSize) // 65507 is max size data for datagram
                protocolManager.send(data, true, returnAddress)
                logger.info("Sent data back")
            }
            TypeData.EXIT -> {
                logger.info("Received exit message, removing $returnAddress")
                returnAddress = null
                protocolManager.resetIndex()
            }
            TypeData.EOFILE -> {
                //Ignore (receive should not return this)
            }
            TypeData.UNKNOWN -> {
                //Ignore
                logger.warn("Get UNKNOWN type!!!")
            }
        }
    }

}