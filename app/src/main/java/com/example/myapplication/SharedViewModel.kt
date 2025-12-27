package com.example.myapplication

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.Socket
import java.nio.ByteBuffer
import java.util.zip.CRC32
import java.util.zip.Deflater
import java.util.zip.Inflater
import java.util.Locale

data class GunInfo(
    val gunNum: Int,
    val lts: Int,
    val ns: Int,
    val mk: String,
    val hu: String,
    var done: Boolean = false // new field for checkbox
)


data class TargetCommand(
    val targetName: String,
    val guns: List<GunInfo>,
    var orderText: String = "",
    val commandType: String // Combines mode and correction info (e.g., "bf", "bf_correction")
)

private data class ParsedPacket(
    val senderId: String,   // <- CHANGE TO STRING
    val commandId: Int,
    val payload: String
)

private class ParseException(message: String) : Exception(message)

class SharedViewModel : ViewModel() {

    private val _logMessages = MutableLiveData<String>()
    val logMessages: LiveData<String> = _logMessages

    private val _incomingCommand = MutableLiveData<TargetCommand>()
    val incomingCommand: LiveData<TargetCommand> = _incomingCommand


    private var tcpSocket: Socket? = null
    private var input: DataInputStream? = null
    private var output: DataOutputStream? = null

    private val serverIP = "10.42.0.1"
    private val serverPort = 5000

    var senderId: Int = 3
    val senderIdHex: String
        get() = "0x%03X".format(senderId)

    init {
        startTcpListener()
    }

    /** ------------- TCP RECEIVE LOOP ------------- */
    private fun startTcpListener() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                tcpSocket = Socket(serverIP, serverPort)
                input = DataInputStream(BufferedInputStream(tcpSocket!!.getInputStream()))
                output = DataOutputStream(BufferedOutputStream(tcpSocket!!.getOutputStream()))
                _logMessages.postValue("Connected to TCP $serverIP:$serverPort")

                val inp = input!!
                while (!tcpSocket!!.isClosed) {
                    val expectedLength = try {
                        readLengthPrefix(inp)
                    } catch (e: Exception) {
                        _logMessages.postValue("⚠️ Invalid length prefix: ${e.message}")
                        continue
                    }

                    val packetData = ByteArray(expectedLength)
                    try {
                        inp.readFully(packetData)
                    } catch (e: Exception) {
                        _logMessages.postValue("⚠️ Stream ended while reading $expectedLength bytes: ${e.message}")
                        break
                    }

                    try {
                        val parsed = parsePacketStrict(packetData)
                        _logMessages.postValue("✅ TCP ← cmd=0x${parsed.commandId.toUInt().toString(16)}, bytes=${packetData.size}")

                        val result = parsePayloadForSender(parsed.payload, senderIdHex)
                        result?.let {
                            _incomingCommand.postValue(it)
                        }


                        // ✅ Only send ACK if the payload query is for this client
                        if (payloadIsForThisDevice(parsed.payload, senderIdHex)) {
                            sendAck(parsed.senderId, parsed.commandId)
                            _logMessages.postValue("📨 Sent ACK for cmd=0x${parsed.commandId.toUInt().toString(16)}")
                        }

                    } catch (e: ParseException) {
                        _logMessages.postValue("❌ Parse error: ${e.message}")
                    } catch (e: Exception) {
                        _logMessages.postValue("❌ Parse unexpected error: ${e.localizedMessage}")
                    }

                }
            } catch (e: Exception) {
                _logMessages.postValue("TCP stopped: ${e.localizedMessage}")
            }
        }
    }

    private fun payloadIsForThisDevice(payload: String,                                                                                                   senderId: String): Boolean {
        val parts = payload.split(":")
        if (parts.size < 2) return false
        val targetId = parts[1]
        return targetId == senderIdHex
    }
    private fun sendAck(targetDeviceId: String, commandId: Int) {
        val ackPayload = "ACK:$senderIdHex:$commandId"
        sendPacket(commandId, ackPayload)
        _logMessages.postValue("📤 Sent ACK to device $targetDeviceId for cmd=0x${commandId.toUInt().toString(16)}")
    }



    /** Read ASCII decimal digits until ':' and return the integer length. */
    private fun readLengthPrefix(inp: DataInputStream): Int {
        val sb = StringBuilder()
        while (true) {
            val b = inp.readByte() // throws on EOF
            val c = b.toInt().toChar()
            if (c == ':') break
            if (c !in '0'..'9') throw ParseException("non-digit in length prefix: '$c'")
            sb.append(c)
            if (sb.length > 10) throw ParseException("length prefix too long")
        }
        if (sb.isEmpty()) throw ParseException("empty length prefix")
        val len = sb.toString().toIntOrNull() ?: throw ParseException("not an int: '${sb}'")
        if (len <= 0) throw ParseException("non-positive length: $len")
        return len
    }

    /** ------------- PACKET PARSING (STRICT) ------------- */
    private fun parsePacketStrict(data: ByteArray): ParsedPacket {
        if (data.size < 11) throw ParseException("packet too short (<11), got ${data.size}")

        val buf = ByteBuffer.wrap(data) // big-endian by default
        val senderIdRaw = String.format(Locale.US, "%03d", buf.get().toInt() and 0xFF)
        val commandIdRaw = buf.int
        val payloadLen = buf.short.toInt() and 0xFFFF

        val needed = 7 + payloadLen + 4
        if (data.size != needed) {
            throw ParseException("length mismatch: header says $needed bytes (payload=$payloadLen), received ${data.size}")
        }

        val payloadCompressed = ByteArray(payloadLen)
        buf.get(payloadCompressed)

        val recvCrc = buf.int
        val calcCrc = computeCRC(data.copyOfRange(0, 7 + payloadLen))
        if (recvCrc.toUInt() != calcCrc.toUInt()) {
            val r = "0x" + recvCrc.toUInt().toString(16)
            val c = "0x" + calcCrc.toUInt().toString(16)
            throw ParseException("CRC mismatch: recv=$r, calc=$c")
        }

        val payload = try {
            decompressPayload(payloadCompressed)
        } catch (e: Exception) {
            throw ParseException("decompress failed: ${e.message}")
        }

        return ParsedPacket(senderIdRaw, commandIdRaw, payload)
    }

    private fun validateCRC(data: ByteArray, receivedCrc: Int): Boolean {
        val calc = computeCRC(data)
        return calc.toUInt() == receivedCrc.toUInt()
    }

    private fun computeCRC(data: ByteArray): Int {
        val crc32 = CRC32()
        crc32.update(data)
        val base = crc32.value
        val finalCrc = base xor 0xA5A5A5A5L
        return finalCrc.toInt()
    }

    private fun decompressPayload(compressed: ByteArray): String {
        val inflater = Inflater() // expects zlib wrapper (matches Python zlib.compress)
        return try {
            inflater.setInput(compressed)
            val out = ByteArray(1024 * 64)
            val len = inflater.inflate(out)
            String(out, 0, len)
        } finally {
            inflater.end()
        }
    }

    private fun compressPayload(payload: String): ByteArray {
        val deflater = Deflater() // zlib format (matches Python zlib.compress)
        return try {
            val input = payload.toByteArray()
            deflater.setInput(input)
            deflater.finish()
            val out = ByteArray(1024 * 64)
            val len = deflater.deflate(out)
            out.copyOf(len)
        } finally {
            deflater.end()
        }
    }

    private fun parsePayloadForSender(payload: String, senderId: String): TargetCommand? {
        val parts = payload.split("|||").map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.isEmpty()) return null

        val mode = parts.find { it.startsWith("M=") }?.removePrefix("M=")?.trim()
        val isCorrection = parts.any { it == "C=t" }
        val commandType = if (isCorrection) "${mode}_correction" else mode

        /** is correction sended is redudant but done for keeping existing functionality i had before*/
        return when (mode) {
            "bf" -> handleBfCommand(parts, senderId, commandType, isCorrection)
            "sf" -> handleSfCommand(parts, senderId, commandType, isCorrection)
            "af" -> handleAfCommand(parts, senderId, commandType, isCorrection)
            else -> {
                _logMessages.postValue("⚠️ Unknown mode: $mode")
                null
            }
        }
    }

    private fun parseGunCommand(parts: List<String>, senderId: String, commandType: String): TargetCommand? {
        val first = parts[0]
        if (!first.startsWith("TARGET=")) return null
        val targetName = first.removePrefix("TARGET=").trim()

        var orderText = ""
        val infos = mutableListOf<GunInfo>()

        for (entry in parts.drop(1)) {
            if (entry.startsWith("O_T=")) {
                orderText = entry.removePrefix("O_T=").trim()
                continue
            }

            // Strictly match gun ID in 0xNNN format
            val gunIdMatch = Regex("Հր\\.:\\s*(0x[0-9A-Fa-f]{3})").find(entry) ?: continue
            val gunNumberStr = gunIdMatch.groupValues[1].lowercase(Locale.US)

            // Only process if this gun matches our senderId
            if (gunNumberStr != senderId.lowercase(Locale.US)) continue

            val gunNumberInt = gunNumberStr.removePrefix("0x").toIntOrNull(16) ?: continue

            // Parse other fields
            val lts = Regex("Լց\\.\\s*:\\s*(\\d+)").find(entry)?.groupValues?.get(1)?.toIntOrNull() ?: continue
            val ns = Regex("Նշ\\.\\s*:\\s*(\\d+)").find(entry)?.groupValues?.get(1)?.toIntOrNull() ?: continue
            val mk = Regex("Մկ\\.\\s*:\\s*([\\d\\-]+)").find(entry)?.groupValues?.get(1) ?: continue
            val hu = Regex("Հ\\.ու\\.\\s*:\\s*([^|]+)").find(entry)?.groupValues?.get(1)?.trim() ?: continue

            infos.add(GunInfo(gunNum = gunNumberInt, lts = lts, ns = ns, mk = mk, hu = hu))
        }

        if (infos.isEmpty() && commandType != "af_correction") return null // Return null if no guns are present, unless it's an AF correction

        return TargetCommand(targetName, infos, orderText, commandType)
    }


    private fun handleBfCommand(parts: List<String>, senderId: String, commandType: String, isCorrection: Boolean): TargetCommand? {
        if (isCorrection) {
            _logMessages.postValue("Correction received for bf mode. Functionality to be added later.")
            return null // Or return a specific command indicating correction received
        }
        return parseGunCommand(parts, senderId, commandType)
    }

    private fun handleSfCommand(parts: List<String>, senderId: String, commandType: String, isCorrection: Boolean): TargetCommand? {
        if (isCorrection) {
            _logMessages.postValue("Correction received for sf mode. Functionality to be added later.")
            return null // Or return a specific command indicating correction received
        }
        return parseGunCommand(parts, senderId, commandType)
    }

    private fun handleAfCommand(parts: List<String>, senderId: String, commandType: String, isCorrection: Boolean): TargetCommand? {
        if (isCorrection) {
            val targetName = parts.find { it.startsWith("TARGET=") }?.removePrefix("TARGET=")?.trim() ?: return null
            var orderText = parts.find { it.startsWith("O_T=") }?.removePrefix("O_T=")?.trim() ?: ""
            return TargetCommand(targetName, emptyList(), orderText, commandType)
        }
        return parseGunCommand(parts, senderId, commandType)
    }

    /** ------------- SENDING ------------- */
    fun sendPacket(commandId: Int, payload: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val compressed = compressPayload(payload)
                val payloadLen = compressed.size

                // header: !B I H  (senderId, commandId, payload_len) big-endian
                val header = ByteBuffer.allocate(7).apply {
                    put(senderId.toByte())
                    putInt(commandId)
                    putShort(payloadLen.toShort())
                }.array()

                val crc = computeCRC(header + compressed)

                val inner = ByteBuffer.allocate(7 + payloadLen + 4).apply {
                    put(header)
                    put(compressed)
                    putInt(crc)
                }.array()

                // ASCII length prefix: "<len>:"
                val prefix = "${inner.size}:".toByteArray(Charsets.US_ASCII)
                val framed = prefix + inner

                output?.write(framed)
                output?.flush()
                _logMessages.postValue("📤 Sent cmd=0x${commandId.toUInt().toString(16)}, bytes=${inner.size}, payload_len=$payloadLen")
            } catch (e: Exception) {
                _logMessages.postValue("⚠️ Send error: ${e.localizedMessage}")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        try { tcpSocket?.close() } catch (_: Exception) {}
    }
}
