package icmp_protocol

import kotlinx.cinterop.*
import platform.posix.*
import platform.linux.inet_pton
import kotlin.random.Random

class IcmpProtocol {
    private val sock = socket(AF_INET, SOCK_DGRAM, IPPROTO_ICMP)
    private val bufferSize = 2048
    fun ping(endPoint: String) {
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
                memset(this.ptr, 0, sizeOf<icmphdr>().convert())
                type = ICMP_ECHO.convert()
                code = 0.convert()
                un.echo.id = Random.nextInt().toShort().convert()
                un.echo.sequence = 0.convert()
            }
            val data = ByteArray(bufferSize)
            while (true) {
                lateinit var readSet: fd_set
                lateinit var timeout: timeval
                lateinit var rcvHeader: icmphdr

                with(timeout) {
                    tv_sec = 1
                    tv_usec = 0
                }
                icmpHeader.un.echo.sequence++
                memcpy(data.refTo(0), icmpHeader.ptr, sizeOf<icmphdr>().convert())
                memcpy(data.refTo(sizeOf<icmphdr>().toInt()), "Hello".cstr, 5)
                var rc: Long = sendto(sock, data.refTo(0), (sizeOf<icmphdr>() + 5).convert(), 0, addr.rawPtr.toLong().toCPointer(), sizeOf<sockaddr_in>().convert())
                if (rc <= 0) {
                    perror("sendto")
                    break
                }
                println("Sending package")
                memset(readSet.ptr, 0, sizeOf<fd_set>().convert())
                posix_FD_SET(sock, readSet.ptr)
                //wait for a reply with a timeout
                rc = select(sock + 1, readSet.ptr, null, null, timeout.ptr).toLong()
                if (rc == 0L) {
                    println("Timeout")
                } else if (rc < 0L) {
                    perror("select")
                    break
                }
                var sLen: socklen_t = 0.convert()
                rc = recvfrom(sock, data.refTo(0), data.size.convert(), 0, null, sLen.objcPtr().toLong().toCPointer())
                if (rc <= 0) {
                    perror("recvfrom");
                    break;
                } else if (rc < sizeOf<icmphdr>() ) {
                    println("Error, got short ICMP packet, $rc bytes\n");
                    break;
                }
                memcpy(rcvHeader.ptr, data.refTo(0), sizeOf<icmphdr>().convert())
                if (rcvHeader.type.toInt() == ICMP_ECHOREPLY) {
                    println("ICMP Reply, id=${icmpHeader.un.echo.id.toString(16)}, " +
                            "sequence = ${icmpHeader.un.echo.sequence.toString(16)}\n")
                } else {
                    println("Got ICMP packet with type ${rcvHeader.type.toString(16)}")
                }
            }
        }
    }
}