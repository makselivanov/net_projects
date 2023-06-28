package client

import icmp_protocol.IcmpProtocol
import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.required

fun main(args: Array<String>) {
    val parser = ArgParser("ping")
    val hostname by parser.option(ArgType.String, "hostname", "h", "Hostname/Ip for ping").required()
    parser.parse(args)
    val icmpManager = IcmpProtocol()
    icmpManager.ping(hostname)
}