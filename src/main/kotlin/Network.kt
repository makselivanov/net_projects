import kotlinx.coroutines.Job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.random.Random

class Network {
    val networkNodes = mutableListOf<NetworkNode>()
    fun generate(networks: Int) {
        if(networks <= 1)
            throw IllegalArgumentException("Cannot create a network with less than two networks!")
        networkNodes.clear()

        val uniqueIPs: List<String> = generateUniqueIPs(networks)
        println("Generated IP Addresses: ")
        uniqueIPs.forEach {
            println(it)
            networkNodes.add(NetworkNode(it, this))
        }

        println("Generated connections: ")
        for (network in 0..<networks) {
            var r = Random.nextInt(networks)
            while (r == network)
                r = Random.nextInt(networks)
            val curNode = networkNodes[network]
            val targetNode = networkNodes[r]
            val edge = Edge(curNode, targetNode)
            curNode.addEdge(edge)
            targetNode.addEdge(edge)

            println("${curNode.ipAddress} -- ${targetNode.ipAddress}")
        }
    }

    fun addLink(fromIP: String, toIP: String) {
        val fromSelected: NetworkNode? = findNode(fromIP)
        val toSelected: NetworkNode? = findNode(toIP)
        if (fromSelected == null || toSelected == null) {
            println("One or both routers are not found!")
            return
        }
        val edge = Edge(fromSelected, toSelected)
        fromSelected.addEdge(edge)
        toSelected.addEdge(edge)
        println("Link added!")
    }
    
    fun removeLink(fromIP: String, toIP: String) {
        val fromSelected: NetworkNode? = findNode(fromIP)
        val toSelected: NetworkNode? = findNode(toIP)
        if (fromSelected == null || toSelected == null) {
            println("One or both routers are not found!")
            return
        }

        val edge = fromSelected.edges.find { it.left == toSelected || it.right == toSelected }
        if (edge == null) {
            println("Link not found!")
            return
        }
        fromSelected.removeEdge(edge)
        toSelected.removeEdge(edge)
        println("Link removed!")
    }

    fun addRouter(ip: String) {
        val node: NetworkNode? = findNode(ip)

        if (node != null) {
            println("Router already exists!")
            return
        }
        networkNodes.add(NetworkNode(ip, this))
        println("Router $ip added!")
    }

    fun removeRouter(ip: String) {
        val node: NetworkNode? = findNode(ip)

        if (node == null) {
            println("Router already exists!")
            return
        }

        networkNodes.remove(node)
        networkNodes.forEach {ns ->
            ns.edges.removeAll {
                it.right == node || it.left == node
            }
        }
        println("Router $ip removed!")
    }

    fun printNodes() {
        networkNodes.forEach { node ->
            println("Router: ${node.ipAddress}")
            println("Connections:")
            node.edges.forEach { edge ->
                val otherNode: NetworkNode = if (edge.left == node) edge.right else edge.left
                println("${node.ipAddress} -- ${otherNode.ipAddress}")
            }
            println()
        }
    }

    fun simulate(simulationCycles: Int) {
        networkNodes.forEach { it.initTable() }

        runBlocking {
            val jobs = mutableListOf<Job>()
            networkNodes.forEach { node ->
                node.simulationCycles = simulationCycles
                jobs.add(launch {
                    node.simulate()
                })
            }

            jobs.joinAll()

            networkNodes.forEach {
                println("Final state of router ${it.ipAddress} table:")
                it.printTable()
            }
        }
    }

    private fun generateUniqueIPs(amount: Int): List<String> {
        val uniqueIPS = mutableListOf<String>()
        for (size in 0..< amount) {
            var ipAddr: String = generateIPAddress()
            while (uniqueIPS.contains(ipAddr)) {
                ipAddr = generateIPAddress()
            }
            uniqueIPS.add(ipAddr)
        }
        return uniqueIPS.toList()
    }

    suspend fun printTable(ip: String) {
        val selected: NetworkNode? = findNode(ip)

        if (selected == null) {
            println("Router not found!")
            return
        }

        selected.printTable()
    }

    private fun findNode(ip: String): NetworkNode? {
        return networkNodes.find {
            it.ipAddress == ip
        }
    }

    private fun generateIPAddress(): String {
        return "${Random.nextInt(1, 255)}.${Random.nextInt(1, 255)}.${Random.nextInt(1, 255)}.${Random.nextInt(1, 255)}"
    }
}