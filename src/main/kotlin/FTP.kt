import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.util.cio.*
import io.ktor.utils.io.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Paths
import kotlin.reflect.KFunction

private const val SERVER = "127.0.0.1"
private const val PORT = 21
class FTP {
    private lateinit var selectorManager: SelectorManager
    private lateinit var socket: Socket
    private lateinit var receiveChannel: ByteReadChannel
    private lateinit var sendChannel: ByteWriteChannel
    private lateinit var job: Job
    private lateinit var logger: Logger
    val commands: Map<String, KFunction<Any>> = mapOf(
        "help" to this::ftp_help,
        "mkdir" to this::ftp_mkdir,
        "rmdir" to this::ftp_rmddir,
        "ls" to this::ftp_ls,
        "lpwd" to this::lpwd,
        "cd" to this::ftp_cd,
        "recv" to this::ftp_recv,
        "send" to this::ftp_send,
    )
    private fun get_code(response: String?): Int? {
        return response?.substring(0, 3)?.toIntOrNull()
    }
    private fun get_message(response: String?): String {
        return response?.substring(4) ?: ""
    }

    fun connect(user: String, password: String): Boolean {
        logger = LoggerFactory.getLogger(javaClass)
        return runBlocking {
            selectorManager = SelectorManager(Dispatchers.IO)
            socket = aSocket(selectorManager).tcp().connect(SERVER, PORT)
            receiveChannel = socket.openReadChannel()
            sendChannel = socket.openWriteChannel(autoFlush = true)
            logger.info("Open TCP connection \nFrom: ${socket.localAddress}\n To: ${socket.remoteAddress}")

            sendChannel.writeStringUtf8("USER $user\r\n")
            logger.info("Trying to login by username \"$user\"")
            withContext(Dispatchers.IO) {
                Thread.sleep(5)
            }
            while (receiveChannel.availableForRead > 0) {
                val response = receiveChannel.readUTF8Line()
                logger.info("RESPONSE: $response")
            }
            sendChannel.writeStringUtf8("PASS $password\r\n")
            logger.info("Entering password")
            withContext(Dispatchers.IO) {
                Thread.sleep(20)
            }
            var response: String? = null
            while (receiveChannel.availableForRead > 0) {
                response = receiveChannel.readUTF8Line()
                logger.info("RESPONSE: $response")
            }
            val code = get_code(response)
            return@runBlocking code != null && code < 400
        }
    }

    fun lpwd(): String {
        return Paths.get("").toAbsolutePath().toString()
    }

    fun ftp_ls(name: String? = null): String { //LIST
        return runBlocking {
            sendChannel.writeStringUtf8("PASV\r\n")
            logger.info("Send command PASV")
            withContext(Dispatchers.IO) {
                Thread.sleep(5)
            }
            val response = receiveChannel.readUTF8Line()
            logger.info("RESPONSE: $response")
            var message = get_message(response)
            message = message.removePrefix("Entering Passive Mode (")
            message = message.removeSuffix(")")
            val numbers = message.split(",").map { it.toInt() }
            val size = numbers.size
            val port = numbers[size - 2].shl(8) + numbers[size - 1]
            val dataSocket = aSocket(selectorManager).tcp().connect(SERVER, port)
            logger.info("Create data socket from ${dataSocket.localAddress} to ${dataSocket.remoteAddress}")
            val dataReadChannel = dataSocket.openReadChannel()
            if (name == null) {
                sendChannel.writeStringUtf8("LIST\r\n")
                logger.info("Send command LIST")
            } else {
                sendChannel.writeStringUtf8("LIST $name\r\n")
                logger.info("Send command LIST $name")
            }

            withContext(Dispatchers.IO) {
                Thread.sleep(5)
            }
            while (receiveChannel.availableForRead > 0) {
                val response = receiveChannel.readUTF8Line()
                logger.info("RESPONSE: $response")
            }
            var data = ""
            try {
                while (dataReadChannel.availableForRead > 0) {
                    data += dataReadChannel.readUTF8Line()
                    data += "\n"
                }
            } finally {
                withContext(Dispatchers.IO) {
                    dataSocket.close()
                }
            }
            logger.info("DATA received with size of ${data.length} bytes")
            return@runBlocking data
        }
    }

    fun ftp_mkdir(name: String): String { //MKD
        return runBlocking {
            sendChannel.writeStringUtf8("MKD $name\r\n")
            logger.info("Send command MKD $name")
            withContext(Dispatchers.IO) {
                Thread.sleep(5)
            }
            var response: String? = null
            while (receiveChannel.availableForRead > 0) {
                response = receiveChannel.readUTF8Line()
                logger.info("RESPONSE: $response")
            }
            return@runBlocking get_message(response)
        }
    }

    fun ftp_rmddir(name: String): String { //RMD
        return runBlocking {
            sendChannel.writeStringUtf8("RMD $name\r\n")
            logger.info("Send command RMD $name")
            withContext(Dispatchers.IO) {
                Thread.sleep(5)
            }
            var response: String? = null
            while (receiveChannel.availableForRead > 0) {
                response = receiveChannel.readUTF8Line()
                logger.info("RESPONSE: $response")
            }
            return@runBlocking get_message(response)
        }
    }

    fun ftp_cd(name: String): String { //CWD
        return runBlocking {
            sendChannel.writeStringUtf8("CWD $name\r\n")
            logger.info("Send command CWD $name")
            withContext(Dispatchers.IO) {
                Thread.sleep(5)
            }
            var response: String? = null
            while (receiveChannel.availableForRead > 0) {
                response = receiveChannel.readUTF8Line()
                logger.info("RESPONSE: $response")
            }
            return@runBlocking get_message(response)
        }
    }

    fun ftp_help(): String { //help
        return "Commands: ${commands.keys.joinToString(", ")}"
    }

    fun ftp_send(localPath: String, remotePath: String? = null): String { // upload //PASV|PORT and STOR
        return runBlocking {
            sendChannel.writeStringUtf8("PASV\r\n")
            logger.info("Send command PASV")
            withContext(Dispatchers.IO) {
                Thread.sleep(5)
            }
            val response = receiveChannel.readUTF8Line()
            logger.info("RESPONSE: $response")
            var message = get_message(response)
            message = message.removePrefix("Entering Passive Mode (")
            message = message.removeSuffix(")")
            val numbers = message.split(",").map { it.toInt() }
            val size = numbers.size
            val port = numbers[size - 2].shl(8) + numbers[size - 1]
            val dataSocket = aSocket(selectorManager).tcp().connect(SERVER, port)
            logger.info("Create data socket from ${dataSocket.localAddress} to ${dataSocket.remoteAddress}")
            val dataWriteChannel = dataSocket.openWriteChannel()
            if (remotePath == null) {
                sendChannel.writeStringUtf8("STOR $localPath\r\n")
                logger.info("Send command STOR $localPath")
            } else {
                sendChannel.writeStringUtf8("STOR $remotePath\r\n")
                logger.info("Send command STOR $remotePath")
            }

            withContext(Dispatchers.IO) {
                Thread.sleep(5)
            }
            try {
                val fileReadChannel = File(localPath).readChannel(coroutineContext = Dispatchers.IO)
                fileReadChannel.read { runBlocking {
                    dataWriteChannel.writeAvailable(it)
                } }
            } finally {
                withContext(Dispatchers.IO) {
                    dataSocket.close()
                }
            }
            withContext(Dispatchers.IO) {
                Thread.sleep(20)
            }
            logger.info("File sent")
            while (receiveChannel.availableForRead > 0) {
                val response = receiveChannel.readUTF8Line()
                logger.info("RESPONSE: $response")
            }
            return@runBlocking "File sent"
        }
    }

    fun ftp_recv(localPath: String, remotePath: String? = null): String { //download // RETR
        return runBlocking {
            sendChannel.writeStringUtf8("PASV\r\n")
            logger.info("Send command PASV")
            withContext(Dispatchers.IO) {
                Thread.sleep(5)
            }
            val response = receiveChannel.readUTF8Line()
            logger.info("RESPONSE: $response")
            var message = get_message(response)
            message = message.removePrefix("Entering Passive Mode (")
            message = message.removeSuffix(")")
            val numbers = message.split(",").map { it.toInt() }
            val size = numbers.size
            val port = numbers[size - 2].shl(8) + numbers[size - 1]
            val dataSocket = aSocket(selectorManager).tcp().connect(SERVER, port)
            logger.info("Create data socket from ${dataSocket.localAddress} to ${dataSocket.remoteAddress}")
            val dataReadChannel = dataSocket.openReadChannel()
            if (remotePath == null) {
                sendChannel.writeStringUtf8("RETR $localPath\r\n")
                logger.info("Send command RETR $localPath")
            } else {
                sendChannel.writeStringUtf8("RETR $remotePath\r\n")
                logger.info("Send command RETR $remotePath")
            }

            withContext(Dispatchers.IO) {
                Thread.sleep(5)
            }
            try {
                val fileWriteChannel = File(localPath).writeChannel(coroutineContext = Dispatchers.IO)
                while (dataReadChannel.availableForRead > 0) {
                    dataReadChannel.read {
                        runBlocking { fileWriteChannel.writeAvailable(it) }
                    }
                }
            } finally {
                withContext(Dispatchers.IO) {
                    dataSocket.close()
                }
            }
            withContext(Dispatchers.IO) {
                Thread.sleep(20)
            }
            logger.info("File received")
            while (receiveChannel.availableForRead > 0) {
                val response = receiveChannel.readUTF8Line()
                logger.info("RESPONSE: $response")
            }
            return@runBlocking "File received"
        }
    }

    fun close() {
        if (this::job.isInitialized) {
            runBlocking {
                job.join()
            }
        }
        if (this::socket.isInitialized)
            socket.close()
    }
}