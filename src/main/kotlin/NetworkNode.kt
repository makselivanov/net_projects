import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds

data class NetworkNode(
    val ipAddress: String = "0.0.0.0",
    val currentNetwork: Network
) {
    val edges = mutableListOf<Edge>()
    var simulationCycles = 0

    @Volatile
    lateinit var table: RoutingTable
    fun addEdge(edge: Edge) {
        edges.add(edge)
    }
    fun removeEdge(edge: Edge) {
        edges.remove(edge)
    }
    fun initTable() {
        table = RoutingTable(this, currentNetwork)
    }
    suspend fun simulate() {
        for (cycle in 0..< simulationCycles) {
            table.printTable(cycle + 1)
            broadcast()
            delay(1.seconds)
        }
    }
    suspend fun printTable() {
        table.printTable()
    }
    suspend fun broadcast() {
        edges.forEach { edge ->
            val node: NetworkNode = if (edge.left == this) edge.right else edge.left
            node.receive(this, table)
        }
    }
    suspend fun receive(node: NetworkNode, receivedTable: RoutingTable) {
        synchronized(this) { //maybe private lock but well
            receivedTable.states.forEach { nState ->
                if (nState.nextNode.ipAddress == "0.0.0.0")
                    return@forEach
                table.states = table.states.map { curState ->
                    if (curState.destinationNode != nState.destinationNode) {
                        return@map curState
                    }
                    val targetMetric = nState.metric + 1
                    if (targetMetric < curState.metric) {
                        return@map curState.copy(metric = targetMetric, nextNode = node)
                    }
                    return@map curState
                }.toMutableList()
            }
        }
    }
}