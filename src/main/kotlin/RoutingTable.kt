data class RoutingState(
    val nextNode: NetworkNode,
    val destinationNode: NetworkNode,
    val metric: Int
)

class RoutingTable(val parentNode: NetworkNode, val currentNetwork: Network) {
    companion object {
        val lock = Any()
    }
    val maxHops = 15
    @Volatile
    var states = mutableListOf<RoutingState>()

    init {
        initStates()
    }

    fun initStates() {
        states.clear()

        currentNetwork.networkNodes.forEach { node ->
            if (node == parentNode)
                return@forEach

            var targetMetric = maxHops + 1
            var nextNode = NetworkNode(currentNetwork = currentNetwork)

            run edgeBreak@ {
                parentNode.edges.forEach { edge ->
                    if (edge.left == node) {
                        if (edge.left.ipAddress == "0.0.0.0")
                            return@forEach
                        nextNode = edge.left
                        targetMetric = 1
                        return@edgeBreak
                    }

                    if (edge.right == node) {
                        if (edge.right.ipAddress == "0.0.0.0")
                            return@forEach
                        nextNode = edge.right
                        targetMetric = 1
                        return@edgeBreak
                    }
                }
            }

            states.add(RoutingState(
                nextNode, node, targetMetric
            ))
        }
    }

    suspend fun printTable(step: Int = -1) {
        synchronized(lock) {
            if (step != -1)
                println("Simulation step $step of router ${parentNode.ipAddress}")
            println("[Source IP]\t[Destination IP]\t[Next Hop]\t[Metric]")
            states.forEach { state ->
                println("${parentNode.ipAddress}\t" +
                        "${state.destinationNode.ipAddress}\t\t" +
                        "${state.nextNode.ipAddress}\t\t" +
                        "${if (state.metric >= maxHops + 1) "inf" else state.metric}")
            }
            println()
        }
    }
}