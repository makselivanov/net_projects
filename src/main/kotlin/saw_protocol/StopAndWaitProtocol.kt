package saw_protocol

import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.util.network.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.slf4j.LoggerFactory
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

const val possiblityOfLose = 0.3
const val helloPrefix     = "HELOO:"
const val dataPrefix      = "DATAA:"
const val endOfFilePrefix = "EOFIL:"
const val exitPrefix      = "EXITT:"
const val unknownPrefix   = "UNKNW:"
enum class TypeData {
    HELLO,
    DATA,
    EOFILE,
    EXIT,
    UNKNOWN,
}

const val serverPort = 8888
class StopAndWaitProtocol(name: String = "") {
    data class BufferPacket(
        val prefixHash: Int,
        val dataHash: Int,
        val header: String,
        val data: ByteArray,
    )
    private val selectorManager = SelectorManager(Dispatchers.IO)
    private lateinit var socket: BoundDatagramSocket
    private var sendAddress: InetSocketAddress? = null
    private var timer = 1.seconds
    private var bufferByteSize = 1024 //bytes
    private val sendPrefix = "SND:"
    private val recvPrefix = "ACK:"
    val headerSize = Int.SIZE_BYTES * 2 + sendPrefix.length + 1 + helloPrefix.length
    private var index = 0
    private var fixedPort = false
    private val logger = LoggerFactory.getLogger("SaWProtocol $name")
    fun bind(ip: String = "127.0.0.1", port: Int? = null) {
        if (port != null) {
            socket = aSocket(selectorManager).udp().bind(InetSocketAddress(ip, port))
            fixedPort = true
            logger.info("Binding to $ip:$port, fixed port")
        } else {
            socket = aSocket(selectorManager).udp().bind()
            logger.info("Binding to ${socket.localAddress}, random port")
        }
    }
    fun setTimer(duration: Duration) {
        timer = duration
        logger.info("Change duration to $timer")
    }
    fun setBufferSize(size: Int) {
        bufferByteSize = size
        logger.info("Change size of buffer to $bufferByteSize bytes")
    }
    fun connect(ip: String, port: Int): Boolean {
        sendAddress = InetSocketAddress(ip, port)
        val hostname = socket.localAddress.toJavaAddress().address
        val clientPort = socket.localAddress.toJavaAddress().port
        sendPackage("$hostname:$clientPort".toByteArray(), TypeData.HELLO)
        logger.info("Connected to $ip:$port")
        return true
    }

    fun resetIndex() {
        index = 0
    }
    private fun getIndex(): Int {
        index = 1 - index
        return 1 - index
    }

    /**
     * 4 bytes is hash of header (Int)
     * * 4 bytes is hash of data (Int)
     * 4 bytes is special prefix (Text)
     * 1 byte is Index           (Byte)
     * 6 byte is special prefix for type of data (Text)
     * Data                      (Text/Bytes)
     */
    private fun corrupt(message: BufferPacket): Boolean { /// Check that last 4 bytes is not hash
        return message.dataHash != message.data.contentHashCode() || message.prefixHash != message.header.hashCode()
    }

    private fun isMessageACK(message: BufferPacket): Boolean { // Check that first 4 bytes is special prefix for ACK
        return message.header.startsWith(recvPrefix)
    }

    private fun isMessageSent(message: BufferPacket): Boolean { // Check that first 4 bytes is special prefix for SND
        return message.header.startsWith(sendPrefix)
    }

    private fun getIndexFromMessage(message: BufferPacket): Int? { // Check and return index in message
        val index = message.header.substring(sendPrefix.length, sendPrefix.length + 1).toInt()
        if (index in 0..1) {
            return index
        }
        return null
    }

    private fun getDataFromMessage(message: BufferPacket): ByteArray { // Return data from valid message
        return message.data
    }

    private fun getTypeOfData(message: BufferPacket): TypeData {
        val header = message.header
        return if (header.endsWith(dataPrefix)) {
            TypeData.DATA
        } else if (header.endsWith(helloPrefix)) {
            TypeData.HELLO
        } else if (header.endsWith(endOfFilePrefix)) {
            TypeData.EOFILE
        } else if (header.endsWith(exitPrefix)) {
            TypeData.EXIT
        } else {
            TypeData.UNKNOWN
        }
    }

    private fun makePacket(prefix: String, data: ByteArray = ByteArray(0)): ByteReadPacket {
        assert(prefix.length == sendPrefix.length + 1 + dataPrefix.length)
        return buildPacket {
            writeInt(prefix.hashCode())
            writeInt(data.contentHashCode())
            append(prefix)
            writeFully(data)
        }
    }

    private fun parsePacket(packet: ByteReadPacket): BufferPacket {
        val prefixHash = packet.readInt()
        val dataHash = packet.readInt()
        assert(sendPrefix.length == recvPrefix.length && dataPrefix.length == helloPrefix.length)
        val header = packet.readTextExact(sendPrefix.length + 1 + dataPrefix.length)
        val data = ByteArray(packet.remaining.toInt())
        packet.readFully(data)
        return BufferPacket(prefixHash, dataHash, header, data)
    }

    private suspend fun sendSocket(datagram: Datagram) {
        if (Random.nextDouble() > possiblityOfLose) {
            socket.send(datagram)
        }
    }
    private fun sendPackage(data: ByteArray, typeOfData: TypeData, toAddress: InetSocketAddress? = sendAddress): ByteArray {
        val currentIndex = getIndex()
        val prefix = "$sendPrefix$currentIndex" + when (typeOfData) {
            TypeData.HELLO -> helloPrefix
            TypeData.DATA -> dataPrefix
            TypeData.EOFILE -> endOfFilePrefix
            TypeData.EXIT -> exitPrefix
            TypeData.UNKNOWN -> unknownPrefix
        }
        if (toAddress == null) {
            return ByteArray(0)
        }
        val buffer = BufferPacket(prefix.hashCode(), data.contentHashCode(), prefix, data)
        logger.info("Starting send package with size ${data.size} bytes to address \"${toAddress}\" with timer $timer")
        return runBlocking {
            var response: ByteArray? = null
            while (true) {
                val packet = makePacket(prefix, data)
                sendSocket(Datagram(packet, toAddress)) //Need to be sure that sendAddress is still valid?
                logger.info(
                    "Sent package to address \"${toAddress}\" with timer $timer"
                )
                logger.debug("Parsed package is {}", buffer)
                withTimeoutOrNull(timer) {
                    while (true) {
                        val datagram = socket.receive()
                        val message = parsePacket(datagram.packet)
                        if (!corrupt(message)
                            && isMessageACK(message)
                            && getIndexFromMessage(message) == currentIndex) {
                            response = getDataFromMessage(message)
                            logger.info("Get ACK message")
                            break
                        }
                    }
                } ?: logger.info("Request timed out")
                if (response != null) {
                    break
                }
            }
            return@runBlocking response!!
        }
    }
    fun send(data: ByteArray, chunked: Boolean = false, toAddress: InetSocketAddress? = sendAddress) {
        if (toAddress == null) {
            logger.warn("toAddress is null!")
            return
        }
        if (chunked) {
            val buffers = data.asIterable().chunked(bufferByteSize)
            buffers.forEachIndexed() {index, buffer ->
                logger.info("Sending ${index + 1}/${buffers.size} DATA packages")
                sendPackage(buffer.toByteArray(), TypeData.DATA, toAddress)
            }
            logger.info("Sent all ${buffers.size} DATA packages")
        } else {
            logger.info("Sending single DATA package")
            sendPackage(data, TypeData.DATA, toAddress)
        }
        sendPackage(ByteArray(0), TypeData.EOFILE, toAddress)
    }

    fun disconnect() {
        sendPackage(ByteArray(0), TypeData.EXIT)
        sendAddress = null
    }

    /**
     * Can receive only one connection currently because of global index
     * Can fix it if I make `listen` method and in it create new socket for connection
     * Then response message for hello message would be new ip:port
     */
    private fun receivePackage(): Pair<TypeData, ByteArray> {
        return runBlocking {
            lateinit var data: ByteArray
            lateinit var typeOfData: TypeData
            while (true) {
                val datagram = socket.receive()
                val message = parsePacket(datagram.packet)
                val returnAddress = datagram.address
                logger.info("Receive package from \"$returnAddress\"")
                logger.debug("Parsed package is {}", message)
                if (!corrupt(message)
                    && isMessageSent(message)
                    ) {
                    val messageIndex = getIndexFromMessage(message)
                    if (messageIndex == index) {
                        //Message is new
                        logger.info("Package is correct")
                        val curIndex = getIndex()
                        val prefix = "$recvPrefix$curIndex$dataPrefix"
                        val packet = makePacket(prefix)
                        sendSocket(Datagram(packet, returnAddress))
                        data = getDataFromMessage(message)
                        typeOfData = getTypeOfData(message)
                        break
                    } else {
                        //Message is old
                        logger.info("Package is duplicate")
                        val prefix = "$recvPrefix$messageIndex$dataPrefix"
                        val packet = makePacket(prefix)
                        sendSocket(Datagram(packet, returnAddress))
                    }
                } else {
                    logger.info("Package is corrupted or invalid")
                    logger.debug("Package is: corrupted=${corrupt(message)} | isNotSNDhead=${!isMessageSent(message)} | isNotCorrectIndex=${getIndexFromMessage(message) != index}")
                    logger.debug("Print local/sent data:\n" +
                            "HeaderHash: local=${message.header.hashCode()} | sent=${message.prefixHash}\n" +
                            "  DataHash: local=${message.data.contentHashCode()} | sent=${message.dataHash}\n" +
                            "     Index: local=${index} | sent=${getIndexFromMessage(message)}\n" +
                            "      Data: ${message.data}")
                    logger.debug("")
                }

            }
            return@runBlocking Pair(typeOfData, data)
        }
    }

    fun receive(): Pair<TypeData, ByteArray> {
        var buffer = ByteArray(0)
        var type = TypeData.UNKNOWN
        while (true) {
            val (typeOfData, data) = receivePackage()
            when (typeOfData) {
                TypeData.HELLO -> {
                    buffer += data
                    type = TypeData.HELLO
                    break
                }
                TypeData.DATA -> {
                    buffer += data
                    type = TypeData.DATA
                }
                TypeData.EOFILE -> {
                    buffer += data
                    break
                }
                TypeData.EXIT -> {
                    buffer += data
                    type = TypeData.EXIT
                    break
                }
                TypeData.UNKNOWN -> {
                    logger.warn("Get UNKNOWN type!!!")
                }
            }
        }
        return Pair(type, buffer)
    }
}