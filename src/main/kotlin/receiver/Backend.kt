package receiver

import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.time.Duration.Companion.seconds

class Backend {
    private var ipAddress: String = "127.0.0.1"
    private var port: Int = 8080
    private lateinit var selectorManager: SelectorManager
    private lateinit var timeEndTCP: LocalTime
    private val numbytesinbuffer = 100000000
    private fun tcpCreateServer(): ServerSocket {
        selectorManager = SelectorManager(Dispatchers.IO)
        return aSocket(selectorManager).tcp().bind(ipAddress, port)
    }

    private suspend fun listeningForConnections(server: ServerSocket): ByteArray {
        val client = server.accept()
        val receiveBytes = tcpReceiveData(client)
        terminateConnections(client)
        return receiveBytes
    }

    private suspend fun tcpReceiveData(socket: Socket): ByteArray {
        val dataBytes = ByteArray(numbytesinbuffer)
        val readChannel = socket.openReadChannel()
        var size: Int
        do {
            size = readChannel.readAvailable(dataBytes)
        } while (readChannel.availableForRead > 0)
        timeEndTCP = LocalTime.now()
        return dataBytes.copyOf(size)
    }

    private fun terminateConnections(socket: Socket) {
        socket.close()
    }

    fun receiveClick(ipAddressTxt: String, portTxt: String) {
        try {
            ipAddress = ipAddressTxt
            port = portTxt.toInt()
            val server = tcpCreateServer()
            try {
                runBlocking {
                    val numBytesTcp = getNumBytes(listeningForConnections(server))
                    val infoAboutConnection = listeningForConnections(server)
                    printInfo(numBytesTcp, infoAboutConnection)
                    delay(1.seconds)
                }
            } catch (ex: Exception) {
                App.textDialog.value = "Проблема при получении: $ex"
                App.showDialog.value = true
            } finally {
                server.close()
                selectorManager.close()
            }
        } catch (ex: Exception) {
            App.textDialog.value = "Проблема при создании сервера: $ex"
            App.showDialog.value = true
        }
    }

    private fun printInfo(numReceiveBytesByTcp: Int, infoAboutConnection: ByteArray) {
        val info = String(infoAboutConnection)
        val pattern = "HH:mm:ss:SSSSSS"
        val tmp = info.substring(0, pattern.length)
        val formatter = DateTimeFormatter.ofPattern(pattern)
        val startSendTCP = LocalTime.parse(tmp, formatter)
        val fullPackageSize = info.substring(pattern.length).toInt()
        val diffTime = ChronoUnit.SECONDS.between(startSendTCP, timeEndTCP)
        App.speedTxt.value = fullPackageSize * 1f / diffTime
        App.packagesTxt.value = numReceiveBytesByTcp
        App.fullPackagesTxt.value = fullPackageSize
    }

    private fun getNumBytes(data: ByteArray): Int {
        var index = numbytesinbuffer - 1
        while (index >= 0) {
            if (data.getOrElse(index) { 0.toByte() } != 0.toByte())
                break
            --index
        }
        ++index
        return index
    }

}