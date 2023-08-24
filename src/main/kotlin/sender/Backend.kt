package sender

import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.utils.io.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class Backend {
    private var ipAddress: String = "127.0.0.1"
    private var port: Int = 8080
    private var packages: Int = 0
    private val selectorManager = SelectorManager(Dispatchers.IO)
    private lateinit var timeStartTCP: LocalTime

    private fun connectToTcpServer(numbytes: Int) {
        runBlocking {
            val socket = tcpConnectToServer()
            sendDataByTCP(socket, numbytes)
            terminateConnection(socket)
        }
    }

    private fun terminateConnection(socket: Socket) {
        socket.close()
    }

    private suspend fun sendDataByTCP(socket: Socket, numbytes: Int) {
        if (numbytes != -1) {
            val data = getNumbers(numbytes)
            tcpSendData(socket, data, numbytes)
        } else {
            val data = ByteArray(1)
            tcpSendData(socket, data, numbytes)
        }
    }

    private fun prepToSendInfoAboutConnection(): String {
        var message = ""
        val formatter = DateTimeFormatter.ofPattern("HH:mm:ss:SSSSSS")
        message += timeStartTCP.format(formatter)
        message += packages
        return message
    }

    private fun getNumbers(count: Int): ByteArray {
        return ByteArray(count) {
            (it % 254 + 1).toByte()
        }
    }

    private suspend fun tcpSendData(socket: Socket, data: ByteArray, numbytes: Int) {
        val output = socket.openWriteChannel()
        if (numbytes != -1) {
            timeStartTCP = LocalTime.now()
            output.writeFully(data)
        } else {
            val info = prepToSendInfoAboutConnection()
            output.writeFully(info.encodeToByteArray())
        }
    }

    private suspend fun tcpConnectToServer(): Socket {
        return aSocket(selectorManager).tcp().connect(ipAddress, port)
    }

    fun sendClick(ipAddressTxt: String, portTxt: String, packagesTxt: String) {
        ipAddress = ipAddressTxt
        try {
            packages = packagesTxt.toInt() //or null?
            port = portTxt.toInt() //or null?
            connectToTcpServer(packages)
        } catch (ex: Exception) {
            App.textDialog.value = "Отмена отправки пакетов, причина: $ex"
            App.showDialog.value = true
        }
        Thread.sleep(2500)
        connectToTcpServer(-1) // try catch?
    }
}