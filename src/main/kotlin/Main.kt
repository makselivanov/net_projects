import java.lang.Exception

fun main() {
    println("Be sure server already running")
    print("Enter user: ")
    val name = readln()
    print("Enter password(unsecure): ")
    val password = readln()
    val ftpManager = FTP()
    try {
        if (!ftpManager.connect(name, password)) {
            println("User/Password is not correct")
            return
        }
        println("Enter EOF for exit")
        while (true) {
            print("Enter command: ")
            val line = readlnOrNull() ?: return
            val list = line.split(" ")
            val command = list[0].lowercase()
            val function = ftpManager.commands[command]
            if (function == null) {
                println("Unknown command")
                continue
            }
            try {
                val answer: Any = if (function.parameters.size + 1 == list.size) {
                    function.call(*list.subList(1, list.size).toTypedArray())
                } else {
                    function.callBy(list.subList(1, list.size).zip(function.parameters).associate { Pair(it.second, it.first) })
                }
                println(answer)
            } catch (e: Exception) {
                println("Error: $e")
            }
        }
    } finally {
        ftpManager.close()
    }
}