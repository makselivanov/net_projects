package icmp_protocol

import kotlinx.cinterop.*
import platform.posix.*
import platform.linux.inet_pton
import kotlin.random.Random
import kotlin.system.getTimeNanos
import kotlin.system.measureNanoTime

class IcmpProtocol {
    private val sock = socket(AF_INET, SOCK_RAW, IPPROTO_ICMP) //SOCK_RAW? SOCK_DGRAM
    private val bufferSize = 2048
    private val hopsMax = 30
    private val timeWait = 2 //Seconds
    private fun intIpToStringIp(addr: Int): String {
        var ip = ""
        var buffer = addr
        for (i in 0 until 4) {
            ip += buffer.toUByte()
            if (i != 3)
                ip += "."
            buffer = buffer shr 8
        }
        return ip
    }
    private fun getCheckSum(header: CValuesRef<ByteVar>, size: Long): UShort {
        var result = 0u
        memScoped {
            val ptr = header.getPointer(this)
            val shortPtr = ptr.rawValue.toLong().toCPointer<UShortVar>() ?: return 0u
            val bytePtr = ptr.rawValue.toLong().toCPointer<UByteVar>() ?: return 0u
            val shortSize = size / 2

            for (index in 0 until shortSize) {
                result += shortPtr[index]
            }
            if (size % 2 != 0L) {
                result += bytePtr[size - 1]
            }
            while (result shr 16 != 0u) {
                result = result.and(0xFFFFu) + (result shr 16)
            }
        }
        return result.inv().toUShort()
    }

    fun traceroute(endPoint: String) {
        if (sock < 0) {
            println("Can't create socket")
            return
        }
        memScoped {
            val addr = alloc<sockaddr_in>().apply {
                memset(this.ptr, 0, sizeOf<sockaddr_in>().convert())
                sin_family = AF_INET.convert()
                inet_pton(AF_INET, endPoint, sin_addr.ptr)
            }
            val icmpHeader = alloc<icmphdr>().apply {
                memset(this.ptr, 0, sizeOf<icmphdr>().convert()) //TODO add checksum
                type = ICMP_ECHO.convert()
                code = 0.convert()
                checksum = 0.convert()
                un.echo.id = Random.nextInt().toShort().convert()
                un.echo.sequence = 0.convert() //htons?
            }
            val data = ByteArray(bufferSize)

            for (index in 1..hopsMax) {
                print("${index.toString().padStart(2, ' ')}  ")
                val ttl: IntVar = alloc<IntVar>().apply {
                    value = index //FIXME
                }
                setsockopt(sock, IPPROTO_IP, IP_TTL, ttl.ptr, sizeOf<IntVar>().convert())
                var readSet: fd_set = alloc()
                var timeout: timeval = alloc()
                var rcvHeader: icmphdr = alloc()
                with(timeout) {
                    tv_sec = timeWait.convert()
                    tv_usec = 0
                }
                icmpHeader.un.echo.sequence++
                icmpHeader.checksum = 0.convert()
                memcpy(data.refTo(0), icmpHeader.ptr, sizeOf<icmphdr>().convert())
                val time: LongVar = alloc<LongVar>().apply {
                    value = getTimeNanos()
                }
                memcpy(data.refTo(sizeOf<icmphdr>().toInt()), time.ptr, Long.SIZE_BYTES.convert())
                icmpHeader.checksum = getCheckSum(data.refTo(0), (Long.SIZE_BYTES + sizeOf<icmphdr>()).convert())
                memcpy(data.refTo(0), icmpHeader.ptr, sizeOf<icmphdr>().convert())
                var rc: Long = 0
                val measuredTime = measureNanoTime {
                    rc = sendto(
                        sock,
                        data.refTo(0),
                        (sizeOf<icmphdr>() + Long.SIZE_BYTES).convert(),
                        0,
                        addr.rawPtr.toLong().toCPointer(),
                        sizeOf<sockaddr_in>().convert()
                    )
                    if (rc <= 0) {
                        perror("sendto")
                        return
                    }
                    memset(readSet.ptr, 0, sizeOf<fd_set>().convert())
                    posix_FD_SET(sock, readSet.ptr)
                    //wait for a reply with a timeout
                    rc = select(sock + 1, readSet.ptr, null, null, timeout.ptr).toLong()
                }
                if (rc == 0L) {
                    println("*")
                    continue
                } else if (rc < 0L) {
                    perror("select")
                    return
                }
                //val addrFrom = alloc<sockaddr_in>() ??
                var sLen: socklen_t = 0.convert()
                rc = recvfrom(sock, data.refTo(0), data.size.convert(), 0, null, sLen.objcPtr().toLong().toCPointer())
                if (rc <= 0) {
                    perror("recvfrom");
                    break;
                } else if (rc < sizeOf<icmphdr>() + sizeOf<iphdr>() ) {
                    println("Error, got short IP + ICMP packet, $rc bytes\n");
                    break;
                }
                val ipHeader = alloc<iphdr>()
                memcpy(ipHeader.ptr, data.refTo(0.convert()), sizeOf<iphdr>().convert())
                memcpy(rcvHeader.ptr, data.refTo(sizeOf<iphdr>().convert()), sizeOf<icmphdr>().convert())
                if (rcvHeader.type.toInt() == ICMP_TIME_EXCEEDED || rcvHeader.type.toInt() == ICMP_ECHOREPLY) {
                    val diffTime = (measuredTime / 100000) * 1.0 / 10
                    val source = ipHeader.saddr.toInt()
                    val ip = intIpToStringIp(source)
                    val bufferAddr = alloc<sockaddr_in> {
                        memset(ptr, 0, sizeOf<sockaddr_in>().convert())
                        sin_family = AF_INET.convert()
                        inet_pton(AF_INET, ip, sin_addr.ptr)
                        sin_port = htons(25).convert()
                    }
                    val name = ByteArray(512)
                    val result = getnameinfo(bufferAddr.ptr.toLong().toCPointer(), sizeOf<sockaddr_in>().convert(), name.refTo(0), name.size.convert(), null, 0.convert(), 0)
                    if (result == 0) {
                        println("${name.toKString()} (${ip}) $diffTime ms")
                    } else if (ip == "null") {
                        println("* $diffTime ms")
                    } else {
                        println("$ip $diffTime ms")
                    }
                } else {
                    println("Got ICMP packet with type 0x${rcvHeader.type.toString(16)}")
                }
                if (rcvHeader.type.toInt() == ICMP_ECHOREPLY) {
                    println("Got EchoReply")
                    break
                }
            }
        }
    }
}