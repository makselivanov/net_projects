import pcap.spi.*
import pcap.spi.option.DefaultLiveOptions
import pcap.spi.util.DefaultTimeout
import sun.misc.Signal
import kotlin.system.exitProcess

object Sniffer {
    fun run(): Pair<List<Long>, List<Long>> {
        var startTime: Long? = null
        var prevTime: Long? = null
        val dataInputList = mutableListOf<Long>()
        val dataOutputList = mutableListOf<Long>()
        val service = Service.Creator.create("PcapService")
        /// promiscuous because we want listen to only host's traffic
        val options = DefaultLiveOptions().promiscuous(false)
        print("Interfaces: ")
        service.interfaces().forEach {
            print("${it.name()} ")
        }
        println()
        println("Using \"any\" interface")
        println("Abort program with SIGINT for results")
        val devAny = service.interfaces().first { it.name().lowercase() == "any" }
        val pcapIn = service.live(devAny, options)
        pcapIn.setDirection(Pcap.Direction.PCAP_D_IN)
        val dumpIn = pcapIn.dumpOpen("DumpInput.pcap")
        val pcapOut = service.live(devAny, options)
        pcapOut.setDirection(Pcap.Direction.PCAP_D_OUT)
        val dumpOut = pcapOut.dumpOpen("DumpOutput.pcap")
        val handler = PacketHandler { args: Pcap, header, buffer ->
            val curTime = header.timestamp().second()
            if (startTime == null) {
                startTime = curTime
                prevTime = startTime!! - 1
            }
            while (prevTime!! < curTime) {
                dataInputList.add(0)
                dataOutputList.add(0)
                prevTime = prevTime!! + 1
            }
            if (args == pcapIn) {
                //print("Input ")
                dumpIn.dump(header, buffer)
                dataInputList[dataInputList.size - 1] = dataInputList.last() + header.captureLength()
            } else {
                //print("Output ")
                dumpOut.dump(header, buffer)
                dataOutputList[dataOutputList.size - 1] = dataOutputList.last() + header.captureLength()
            }
            /*println("Frame\n" +
                    "Epoch Time: ${header.timestamp().second()}.${header.timestamp().microSecond()} seconds\n" +
                    "Frame Length: ${header.length()} bytes\n" +
                    "Capture Length: ${header.captureLength()} bytes\n")*/
        }

        val selector = service.selector()
        selector.register(pcapIn)
        selector.register(pcapOut)
        Signal.handle(Signal("INT")) {
            println("Input  stats: ${pcapIn.stats()}")
            println("Output stats: ${pcapOut.stats()}")
            selector.close()
            dumpIn.flush()
            dumpOut.flush()
            dumpIn.close()
            dumpOut.close()
            pcapIn.close()
            pcapOut.close()
            //exitProcess(0)
        }
        try {
            while (true) {
                val timeout: Timeout = DefaultTimeout(1000000L, Timeout.Precision.MICRO)
                val selectables = selector.select(timeout)
                val iterator: Iterator<Selectable> = selectables.iterator()
                while (iterator.hasNext()) {
                    val next = iterator.next()
                    val pcap = next as Pcap
                    pcap.dispatch(1, handler, pcap)
                }
            }
        } finally {
            return Pair(dataInputList, dataOutputList)
        }
    }
}