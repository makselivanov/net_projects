import kotlinx.cli.*

fun main(args: Array<String>) {
    val parser = ArgParser("SMTP Client")
    val from by parser.option(ArgType.String, shortName="f", description = "Email sending from").required()
    val to by parser.option(ArgType.String, shortName = "t", description = "Email sending to").required()
    val imgPath by parser.option(ArgType.String, shortName = "i", description = "Path to image, if you want send it")
    parser.parse(args)
    println("Enter title: ")
    var msg = ""
    do {
        val buffer = readlnOrNull()
        if (buffer != null) {
            msg += buffer
        }
    } while (buffer != null)
    println("End of title")
    val smtp = SmtpProtocol()
    try {
        smtp.connect()
        smtp.send(from, to, msg, imgPath)
    } finally {
        smtp.close()
    }
}