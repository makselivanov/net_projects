import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import kotlinx.coroutines.*
import java.net.NetworkInterface
import kotlin.time.Duration.Companion.seconds

val selectorManager = SelectorManager(Dispatchers.IO)
suspend fun isPortAvailable(hostname: String, port: Int) : Boolean {
    return try {
        var socket: Socket? = null
        val result = withTimeoutOrNull(5.seconds) {
            socket = aSocket(selectorManager).tcp().connect(hostname, port) {
                socketTimeout = 5000
            }
            val receiveChannel = socket!!.openReadChannel()
            receiveChannel.awaitContent()
        }
        socket?.close()
        result != null
    } catch (e: java.net.ConnectException) {
        false
    }
}

fun main() {
    val networks = NetworkInterface.getNetworkInterfaces().toList()
    println("Printing all IP/mask for all interfaces")
    for (curNet in networks) {
        println(curNet.displayName)
        if (curNet.isLoopback) {
            println("\tThis is loopback interface")
        }
        if (curNet.isVirtual) {
            println("\tThis is virtual interface")
        }
        if (curNet.isUp) {
            println("\tThis is running interface")
        }
        curNet.interfaceAddresses.forEach {
            println("\tIP: ${it.address}, mask: ${it.networkPrefixLength}")
        }
        println()
    }

    println("Enter IP for checking open ports, enter EOF for exiting")
    var hostname : String?
    while (true) {
        hostname = readlnOrNull()
        if (hostname == null)
            break

        println("Enter range of ports (2 numbers), enter EOF for exiting")
        println("Will be printing only available ports")
        while (true) {
            val buffer = readlnOrNull() ?: break
            val list = buffer.split(" ")
            if (list.size != 2) {
                println("Not 2 numbers, please enter 2 numbers")
                continue
            }
            val numbers = list.map { it.toIntOrNull() }
            if (numbers.any { it == null }) {
                println("Some of it is not number, please enter 2 numbers")
                continue
            }
            val (left, right) = Pair(numbers[0]!!, numbers[1]!!)
            if (left < 0 || right < 0) {
                println("Some numbers is less than enter")
                continue
            }
            if (left > right) {
                println("Left number is greater than right, please make first less than second")
                continue
            }
            runBlocking {
                val jobs = mutableListOf<Deferred<Boolean>>()
                for (port in left..right) {
                    jobs.add(async {
                        return@async isPortAvailable(hostname, port)
                    })
                }
                jobs.joinAll()
                var portIndex = left
                for (job in jobs) {
                    if (job.await()) {
                        println("\tPort $portIndex is available")
                    }
                    portIndex += 1
                }
                println("Checked all $left..$right ports")
            }
            println("Enter range of ports (2 numbers)")
        }
        println("Enter IP for checking open ports")
    }

}