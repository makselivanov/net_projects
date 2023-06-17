import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.util.*
import kotlin.math.roundToInt


private const val SERVER = "aspmx.l.google.com" //smtp.gmail.com
private const val PORT = 25

class SmtpProtocol {
    private lateinit var socket: Socket
    private lateinit var receiveChannel: ByteReadChannel
    private lateinit var sendChannel: ByteWriteChannel
    private lateinit var job: Job
    private lateinit var logger: Logger
    fun connect() {
        logger = LoggerFactory.getLogger(javaClass)
        runBlocking {
            val selectorManager = SelectorManager(Dispatchers.IO)
            socket = aSocket(selectorManager).tcp().connect(SERVER, PORT)
            receiveChannel = socket.openReadChannel()
            sendChannel = socket.openWriteChannel(autoFlush = true)
            logger.info("Open TCP connection \nFrom: ${socket.localAddress}\n To: ${socket.remoteAddress}")
        }
    }

    private suspend fun send_receive_and_log(message: String, receiving: Boolean = true): Int? {
        logger.info("Sending: \n$message")
        sendChannel.writeStringUtf8("$message\r\n")
        withContext(Dispatchers.IO) {
            Thread.sleep(50)
        }
        var answer: Int? = null
        if (receiving) {
            do {
                val receiveMsg = receiveChannel.readUTF8Line()
                logger.info("Receiving: \n$receiveMsg")
                val buffer = receiveMsg?.substring(0, 3)?.toIntOrNull()
                if (buffer != null)
                    answer = buffer
            } while (receiveChannel.availableForRead > 0)
        }
        return answer
    }
    fun send(from: String, to: String, msg: String, imgPath: String? = null) {
        if (this::job.isInitialized) {
            runBlocking {
                job.join()
            }
        }
        job = CoroutineScope(Dispatchers.IO).launch {
            val guid = UUID.randomUUID()
            val generateNumber = { (Math.random() * 1e7).roundToInt() }
            val boundaryText = "bound.${generateNumber()}.${generateNumber()}.mail.spbu.ru"
            val messages = mutableListOf(
                Pair("HELO ${socket.localAddress.toString().removePrefix("/").split(":")[0]}", true),
                Pair("MAIL FROM:<$from>", true), //<> Is for google SMTP server
                Pair("RCPT To:<$to>", true),
                Pair("DATA", true),
                Pair("From:$from\r\n" +
                        "To:$to\r\n" +
                        "Message-Id:<$guid@mail.gmail.com>\r\n" +
                        "Subject: $msg\r\n" +
                        "MIME-Version: 1.0\r\n" +
                        "Content-Type: multipart/mixed; " +
                        "boundary=\"${boundaryText}\"\r\n" +
                        "--${boundaryText}\r\n" +
                        "Content-Type: text/plain; charset=\"UTF-8\"\r\n\r\n" +
                        "--${boundaryText}", false),
            )
            if (imgPath != null) {
                val imgFile = File(imgPath)
                if (imgFile.exists()) {
                    val encoder = Base64.getEncoder()
                    val encoded = encoder.encodeToString(imgFile.readBytes())
                    messages.add(
                        Pair(
                            "Content-Type: image/png; name=\"test.png\"\r\n" +
                                    "Content-Transfer-Encoding: base64\r\n" +
                                    "Content-ID: <my_testing_image>\r\n" +
                                    "Content-Disposition: attachment; filename=\"test.png\"\r\n" +
                                    "\r\n" + encoded +
                                    "\r\n--${boundaryText}", false
                        )
                    )
                }
            }
            messages.addAll( arrayOf(
                Pair("\r\n.", true),
                Pair("QUIT", true)
            ) )
            for (curMessage in messages) {
                val result = send_receive_and_log(curMessage.first, curMessage.second)
                if (result != null && result >= 400) {
                    break
                }
            }
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