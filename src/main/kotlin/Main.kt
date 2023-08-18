import kotlinx.coroutines.runBlocking
import sun.net.util.IPAddressUtil
import java.util.*

const val DEFAULT_ROUTERAMOUNT: Int = 5
const val DEFAULT_SIMULATIONCYCLES: Int = 4

fun printHelp() {
    println("\nHelp menu:")
    println("[Command]\t\t\t[Description]\n")
    println("'%s'\t\t\t\t%s".format("help", "shows this menu"))
    println("'%s'\t\t\t\t%s".format("exit", "exits the program"))
    println("'%s'\t\t\t\t%s".format("print", "prints out the network"))
    println("'%s'\t\t%s".format("print_table <ip>", "prints out the table of the given router"))
    println("'%s'\t\t%s".format("generate <amount>", "generates a given amount of routers with random connections"))
    println("'%s'\t\t%s".format("simulate <cycles>", "simulates the network with given amount of cycles"))
    println("'%s'\t\t%s".format("add_router <ip>", "adds a router to network"))
    println("'%s'\t\t%s".format("remove_router <ip>", "removes router from network"))
    println("'%s'\t\t%s".format("add_link <ip1> <ip2>", "adds a link between routers"))
    println("'%s'\t%s".format("remove_link <ip1> <ip2>", "removes a link between routers"))

    println("\n")
}

fun getStringArgument(text: String, argument: Int): String?     {
    val args = text.split(' ')
    if (args.size - 1 < argument) {
        println("Invalid amount of arguments!")
        return null
    }
    return args[argument + 1]
}

fun getIntegerArgument(text: String, argument: Int): Int {
    val args = text.split(' ')
    if (args.size - 2 < argument) {
        println("Invalid amount of arguments!")
        return -1
    }

    val ret: Int? = args[argument + 1].toIntOrNull()
    if (ret == null) {
        println("Invalid argument type!")
        return -1
    }
    return ret
}

fun checkIP(ip: String): Boolean {
    return IPAddressUtil.isIPv4LiteralAddress(ip)
}

fun cli() {
    val net = Network()
    println("Welcome to manual control!")
    println("Type 'help' to see the available commands")

    print("Input: ")
    var command = ""
    var fullCommand = ""
    var exit = false

    while (!exit) {
        fullCommand = readlnOrNull()?.lowercase() ?: ""
        command = fullCommand.trim().split(' ')[0]
        when (command) {
            "help" -> {
                printHelp()
            }
            "exit" -> {
                exit = true
            }
            "generate" -> {
                val arg = getIntegerArgument(fullCommand, 0)
                if (arg != -1) {
                    net.generate(arg)
                }
            }
            "simulate" -> {
                val arg = getIntegerArgument(fullCommand, 0)
                if (arg != -1) {
                    net.simulate(arg)
                }
            }
            "print" -> {
                net.printNodes()
            }
            "add_router" -> {
                val ip = getStringArgument(fullCommand, 0)
                if (ip != null && checkIP(ip)) {
                    net.addRouter(ip)
                }
            }
            "remove_router" -> {
                val ip = getStringArgument(fullCommand, 0)
                if (ip != null && checkIP(ip)) {
                    net.removeRouter(ip)
                }
            }
            "print_table" -> {
                val ip = getStringArgument(fullCommand, 0)
                if (ip != null && checkIP(ip)) {
                    runBlocking {
                        net.printTable(ip)
                    }
                }
            }
            "add_link" -> {
                val fromIP = getStringArgument(fullCommand, 0)
                val toIP = getStringArgument(fullCommand, 1)
                if (fromIP != null && checkIP(fromIP) && toIP != null && checkIP(toIP)) {
                    net.addLink(fromIP, toIP)
                }
            }
            "remove_link" -> {
                val fromIP = getStringArgument(fullCommand, 0)
                val toIP = getStringArgument(fullCommand, 1)
                if (fromIP != null && checkIP(fromIP) && toIP != null && checkIP(toIP)) {
                    net.removeLink(fromIP, toIP)
                }
            }
            else -> {
                println("Unrecognized command: $command")
            }
        }
        print("Input: ")
    }
}

fun runSimulation(routerAmount: Int, simulationCycles: Int) {
    val net = Network()
    println("Generating a network with $routerAmount nodes.")
    net.generate(routerAmount)
    println("\nSimulating the generated network...")
    net.simulate(simulationCycles)
}

fun main(args: Array<String>) {
    //val parser = ArgParser("RIP emulator")
    if (args.isEmpty()) {
        println("Running default generated simulation!")
        println("Use 'manual' argument to be able to control it manually!\n")
        runSimulation(DEFAULT_ROUTERAMOUNT, DEFAULT_SIMULATIONCYCLES)
        println("End of execution!")
        return
    } else {
        if (args[0].lowercase(Locale.getDefault()).trim() == "manual")
            cli()
    }
}