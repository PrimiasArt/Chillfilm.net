package com.phyll.filmhub.tvmanager

import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.OutputStream
import java.math.BigInteger
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.concurrent.atomic.AtomicInteger
import javax.crypto.Cipher

data class AdbTvHardwareInfo(
    val model: String = "",
    val brand: String = "",
    val packageInstalled: Boolean = false,
    val appVersion: String = "",
    val needsAuthorization: Boolean = false
)

class AdbUnauthorizedException(message: String) : IOException(message)

/**
 * Pure-Kotlin implementation of the Android Debug Bridge (ADB) over TCP/IP protocol (port 5555).
 *
 * Allows the standalone Android phone app to:
 * - Discover & inspect Smart TV / TV Box hardware without a PC
 * - Push the phone's own APK (`applicationInfo.sourceDir`) directly to TV over Wi-Fi (`SYNC SEND/DATA/DONE`)
 * - Install / update (`pm install -r`), launch (`am start`), and uninstall (`pm uninstall`) the TV app
 */
class AdbTcpClient(private val keyStorageDir: File? = null) {

    fun testConnectionAndProbe(
        host: String,
        port: Int = DEFAULT_ADB_PORT,
        timeoutMs: Int = 15_000,
        allowPublicKeyPrompt: Boolean = false
    ): AdbTvHardwareInfo {
        return try {
            openSession(
                host = host,
                port = port,
                connectTimeoutMs = 1_500,
                readTimeoutMs = timeoutMs,
                allowPublicKeyPrompt = allowPublicKeyPrompt
            ).use { session ->
                val raw = session.runShell(
                    "echo MODEL=\$(getprop ro.product.model); " +
                        "echo BRAND=\$(getprop ro.product.brand); " +
                        "echo PKG=\$(pm path $TARGET_PACKAGE 2>/dev/null); " +
                        "echo VER=\$(dumpsys package $TARGET_PACKAGE 2>/dev/null | grep versionName | head -n 1)"
                )
                parseHardwareInfoOutput(raw)
            }
        } catch (_: AdbUnauthorizedException) {
            AdbTvHardwareInfo(needsAuthorization = true)
        }
    }

    fun pushAndInstallApk(
        host: String,
        port: Int = DEFAULT_ADB_PORT,
        apkFile: File,
        onProgress: (percent: Int, message: String) -> Unit = { _, _ -> }
    ) {
        if (!apkFile.exists() || apkFile.length() <= 0L) {
            throw IOException("Không tìm thấy file APK để cài đặt lên TV")
        }
        val remoteTmp = "/data/local/tmp/phyll_remote_install.apk"
        onProgress(2, "Đang kết nối ADB tới TV $host:$port...")

        openSession(host, port, connectTimeoutMs = 4_000, readTimeoutMs = 95_000).use { session ->
            onProgress(6, "Đang truyền file APK sang TV (${apkFile.length() / (1024 * 1024)} MB)...")
            session.pushFile(apkFile, remoteTmp) { sent, total ->
                val pct = if (total > 0) (8 + ((sent * 64) / total).toInt()).coerceIn(8, 72) else 40
                val mbSent = sent / (1024f * 1024f)
                val mbTotal = total / (1024f * 1024f)
                onProgress(pct, "Đang gửi APK lên TV: %.1f / %.1f MB (%d%%)...".format(mbSent, mbTotal, pct))
            }

            onProgress(76, "TV đang giải nén & cài đặt ứng dụng (vui lòng đợi 5-15 giây)...")
            var installOut = session.runShell("pm install -r -t $remoteTmp")
            if (installOut.contains("Unknown option", ignoreCase = true) ||
                installOut.contains("illegal option", ignoreCase = true)
            ) {
                installOut = session.runShell("pm install -r $remoteTmp")
            }

            // Clean temporary installer file regardless of install output
            runCatching { session.runShell("rm -f $remoteTmp") }

            if (!installOut.contains("Success", ignoreCase = true)) {
                val cleaned = installOut.trim().ifBlank { "TV từ chối cài đặt hoặc chưa bật quyền Install via USB/ADB" }
                throw IOException("Cài APK thất bại: $cleaned")
            }

            onProgress(92, "Cài đặt thành công! Đang khởi chạy Phyll Cinema trên TV...")
            session.runShell("am start -n $TARGET_ACTIVITY")
            onProgress(100, "Đã cài đặt và mở ứng dụng trên TV thành công!")
        }
    }

    fun uninstallPackage(
        host: String,
        port: Int = DEFAULT_ADB_PORT,
        packageName: String = TARGET_PACKAGE,
        onProgress: (percent: Int, message: String) -> Unit = { _, _ -> }
    ) {
        onProgress(15, "Đang kết nối TV $host:$port để gỡ cài đặt...")
        openSession(host, port, connectTimeoutMs = 4_000, readTimeoutMs = 30_000).use { session ->
            onProgress(55, "Đang gỡ ứng dụng $packageName khỏi TV...")
            val out = session.runShell("pm uninstall $packageName")
            if (out.contains("Success", ignoreCase = true) ||
                out.contains("Unknown package", ignoreCase = true) ||
                out.contains("not installed", ignoreCase = true)
            ) {
                onProgress(100, "Đã gỡ ứng dụng khỏi TV!")
            } else {
                val msg = out.trim().ifBlank { "Không thể gỡ ứng dụng" }
                throw IOException("Gỡ app thất bại: $msg")
            }
        }
    }

    fun launchAppOnTv(
        host: String,
        port: Int = DEFAULT_ADB_PORT
    ) {
        openSession(host, port, connectTimeoutMs = 3_500, readTimeoutMs = 12_000).use { session ->
            val out = session.runShell("am start -n $TARGET_ACTIVITY")
            if (out.contains("Error:", ignoreCase = true)) {
                throw IOException(out.trim())
            }
        }
    }

    fun stopAppOnTv(
        host: String,
        port: Int = DEFAULT_ADB_PORT
    ) {
        openSession(host, port, connectTimeoutMs = 3_500, readTimeoutMs = 10_000).use { session ->
            session.runShell("am force-stop $TARGET_PACKAGE")
        }
    }

    private fun openSession(
        host: String,
        port: Int,
        connectTimeoutMs: Int,
        readTimeoutMs: Int,
        allowPublicKeyPrompt: Boolean = true
    ): AdbSession {
        val socket = Socket()
        socket.connect(InetSocketAddress(host, port), connectTimeoutMs)
        socket.soTimeout = readTimeoutMs
        val session = AdbSession(socket, getOrCreateKeyPair())
        try {
            session.handshake(allowPublicKeyPrompt = allowPublicKeyPrompt)
            return session
        } catch (t: Throwable) {
            runCatching { session.close() }
            throw t
        }
    }

    @Synchronized
    private fun getOrCreateKeyPair(): KeyPair {
        val dir = keyStorageDir
        if (dir != null) {
            val privFile = File(dir, "adb_rsa.pk8")
            val pubFile = File(dir, "adb_rsa.x509")
            if (privFile.exists() && pubFile.exists()) {
                val loaded = runCatching {
                    val kf = KeyFactory.getInstance("RSA")
                    val priv = kf.generatePrivate(PKCS8EncodedKeySpec(privFile.readBytes()))
                    val pub = kf.generatePublic(X509EncodedKeySpec(pubFile.readBytes()))
                    KeyPair(pub, priv)
                }.getOrNull()
                if (loaded != null) return loaded
            }
        }

        val kpg = KeyPairGenerator.getInstance("RSA")
        kpg.initialize(2048)
        val generated = kpg.generateKeyPair()
        if (dir != null) {
            runCatching {
                dir.mkdirs()
                File(dir, "adb_rsa.pk8").writeBytes(generated.private.encoded)
                File(dir, "adb_rsa.x509").writeBytes(generated.public.encoded)
            }
        }
        return generated
    }

    class AdbSession(
        private val socket: Socket,
        private val keyPair: KeyPair
    ) : AutoCloseable {

        private val input = BufferedInputStream(socket.getInputStream(), 65_536)
        private val output: OutputStream = socket.getOutputStream()
        private val nextLocalId = AtomicInteger(1)
        var serverMaxData: Int = DEFAULT_MAX_DATA
            private set

        fun handshake(allowPublicKeyPrompt: Boolean = true) {
            val prevTimeout = socket.soTimeout
            socket.soTimeout = 25_000
            try {
                sendPacket(A_CNXN, ADB_VERSION, MAX_REQUEST_DATA, "host::\u0000".toByteArray(Charsets.UTF_8))

                var signatureSent = false
                var pubKeySent = false
                while (true) {
                    val pkt = readPacket()
                    when (pkt.command) {
                        A_CNXN -> {
                            serverMaxData = pkt.arg1.coerceIn(4_096, MAX_REQUEST_DATA)
                            return
                        }
                        A_AUTH -> {
                            if (pkt.arg0 == ADB_AUTH_TOKEN) {
                                if (!signatureSent) {
                                    val sig = signAdbToken(pkt.payload, keyPair.private)
                                    sendPacket(A_AUTH, ADB_AUTH_SIGNATURE, 0, sig)
                                    signatureSent = true
                                } else if (!allowPublicKeyPrompt) {
                                    throw AdbUnauthorizedException("TV chưa xác thực khóa ADB từ điện thoại này")
                                } else if (!pubKeySent) {
                                    val pubPayload = encodeAdbPublicKeyString(keyPair.public as RSAPublicKey)
                                    sendPacket(A_AUTH, ADB_AUTH_RSAPUBLICKEY, 0, pubPayload)
                                    pubKeySent = true
                                    // TV displays "Allow debugging?" dialog - wait patiently up to 35s
                                    socket.soTimeout = 35_000
                                } else {
                                    throw AdbUnauthorizedException("Hãy bấm Cho phép gỡ lỗi USB/Mạng trên màn hình TV rồi thử lại")
                                }
                            }
                        }
                        else -> throw IOException("ADB handshake unexpected packet: 0x${pkt.command.toString(16)}")
                    }
                }
            } finally {
                socket.soTimeout = prevTimeout
            }
        }

        fun runShell(command: String): String {
            val localId = nextLocalId.getAndIncrement()
            openStream(localId, "shell:$command")
            val buf = ByteArrayOutputStream()
            while (true) {
                val pkt = readPacket()
                when (pkt.command) {
                    A_WRTE -> {
                        if (pkt.arg1 == localId) {
                            if (pkt.payload.isNotEmpty()) buf.write(pkt.payload)
                            sendPacket(A_OKAY, localId, pkt.arg0, ByteArray(0))
                        }
                    }
                    A_CLSE -> {
                        if (pkt.arg1 == localId) {
                            sendPacket(A_CLSE, localId, pkt.arg0, ByteArray(0))
                            break
                        }
                    }
                    A_OKAY -> Unit
                }
            }
            return buf.toString("UTF-8")
        }

        fun pushFile(
            localFile: File,
            remotePath: String,
            onProgress: (sentBytes: Long, totalBytes: Long) -> Unit = { _, _ -> }
        ) {
            val localId = nextLocalId.getAndIncrement()
            var remoteId = openStream(localId, "sync:")
            val syncResponses = ByteArrayOutputStream()

            fun writeSyncBytes(data: ByteArray, length: Int = data.size) {
                var offset = 0
                val chunkLimit = serverMaxData.coerceIn(4_096, 65_536)
                while (offset < length) {
                    val sliceLen = minOf(chunkLimit, length - offset)
                    val slice = data.copyOfRange(offset, offset + sliceLen)
                    remoteId = writePacketAwaitOkay(localId, remoteId, slice, syncResponses)
                    offset += sliceLen
                }
            }

            // 1. SYNC SEND header: "SEND" + LE32(specLen) + "$remotePath,33204"
            val spec = "$remotePath,33204".toByteArray(Charsets.UTF_8)
            val sendHdr = ByteBuffer.allocate(8 + spec.size).order(ByteOrder.LITTLE_ENDIAN)
                .put("SEND".toByteArray(Charsets.US_ASCII))
                .putInt(spec.size)
                .put(spec)
                .array()
            writeSyncBytes(sendHdr)

            // 2. SYNC DATA chunks (each DATA chunk <= 32KB)
            val totalBytes = localFile.length()
            var sentBytes = 0L
            val fileBuf = ByteArray(32_768)
            val packetBuf = ByteArray(8 + fileBuf.size)
            packetBuf[0] = 'D'.code.toByte()
            packetBuf[1] = 'A'.code.toByte()
            packetBuf[2] = 'T'.code.toByte()
            packetBuf[3] = 'A'.code.toByte()

            FileInputStream(localFile).use { fis ->
                while (true) {
                    val read = fis.read(fileBuf)
                    if (read <= 0) break
                    packetBuf[4] = (read and 0xFF).toByte()
                    packetBuf[5] = ((read ushr 8) and 0xFF).toByte()
                    packetBuf[6] = ((read ushr 16) and 0xFF).toByte()
                    packetBuf[7] = ((read ushr 24) and 0xFF).toByte()
                    System.arraycopy(fileBuf, 0, packetBuf, 8, read)
                    writeSyncBytes(packetBuf, 8 + read)
                    sentBytes += read
                    onProgress(sentBytes, totalBytes)
                }
            }

            // 3. SYNC DONE packet
            val mtimeSec = (localFile.lastModified() / 1_000L).toInt()
            val donePkt = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
                .put("DONE".toByteArray(Charsets.US_ASCII))
                .putInt(mtimeSec)
                .array()
            writeSyncBytes(donePkt)

            // 4. Read 8-byte sync reply ("OKAY" + len or "FAIL" + errLen + errMsg)
            while (syncResponses.size() < 8) {
                val pkt = readPacket()
                when (pkt.command) {
                    A_WRTE -> {
                        if (pkt.arg1 == localId) {
                            if (pkt.payload.isNotEmpty()) syncResponses.write(pkt.payload)
                            sendPacket(A_OKAY, localId, pkt.arg0, ByteArray(0))
                        }
                    }
                    A_CLSE -> throw IOException("ADB sync stream closed unexpectedly before reply")
                }
            }

            val respBytes = syncResponses.toByteArray()
            val statusTag = String(respBytes, 0, 4, Charsets.US_ASCII)
            if (statusTag == "FAIL") {
                val errLen = ByteBuffer.wrap(respBytes, 4, 4).order(ByteOrder.LITTLE_ENDIAN).int.coerceIn(0, 1024)
                while (syncResponses.size() < 8 + errLen) {
                    val pkt = readPacket()
                    if (pkt.command == A_WRTE && pkt.arg1 == localId) {
                        if (pkt.payload.isNotEmpty()) syncResponses.write(pkt.payload)
                        sendPacket(A_OKAY, localId, pkt.arg0, ByteArray(0))
                    } else if (pkt.command == A_CLSE) break
                }
                val full = syncResponses.toByteArray()
                val errMsg = if (full.size >= 8 + errLen) {
                    String(full, 8, errLen, Charsets.UTF_8)
                } else {
                    "Sync FAIL"
                }
                throw IOException("ADB truyền file thất bại: $errMsg")
            } else if (statusTag != "OKAY") {
                throw IOException("ADB sync phản hồi lạ: $statusTag")
            }

            // 5. Clean QUIT & CLSE
            runCatching {
                val quitPkt = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
                    .put("QUIT".toByteArray(Charsets.US_ASCII))
                    .putInt(0)
                    .array()
                writeSyncBytes(quitPkt)
                sendPacket(A_CLSE, localId, remoteId, ByteArray(0))
            }
        }

        private fun openStream(localId: Int, service: String): Int {
            val payload = "$service\u0000".toByteArray(Charsets.UTF_8)
            sendPacket(A_OPEN, localId, 0, payload)
            while (true) {
                val pkt = readPacket()
                if (pkt.arg1 != localId) continue
                when (pkt.command) {
                    A_OKAY -> return pkt.arg0
                    A_CLSE -> throw IOException("TV từ chối mở luồng ADB ($service)")
                }
            }
        }

        private fun writePacketAwaitOkay(
            localId: Int,
            remoteId: Int,
            data: ByteArray,
            incomingSink: ByteArrayOutputStream
        ): Int {
            sendPacket(A_WRTE, localId, remoteId, data)
            while (true) {
                val pkt = readPacket()
                if (pkt.arg1 != localId) continue
                when (pkt.command) {
                    A_OKAY -> return pkt.arg0
                    A_WRTE -> {
                        if (pkt.payload.isNotEmpty()) incomingSink.write(pkt.payload)
                        sendPacket(A_OKAY, localId, pkt.arg0, ByteArray(0))
                    }
                    A_CLSE -> throw IOException("ADB đóng luồng truyền tải giữa chừng")
                }
            }
        }

        private fun sendPacket(cmd: Int, arg0: Int, arg1: Int, payload: ByteArray) {
            val header = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN)
                .putInt(cmd)
                .putInt(arg0)
                .putInt(arg1)
                .putInt(payload.size)
                .putInt(computeAdbChecksum(payload))
                .putInt(cmd xor -1)
                .array()
            synchronized(output) {
                output.write(header)
                if (payload.isNotEmpty()) {
                    output.write(payload)
                }
                output.flush()
            }
        }

        private fun readPacket(): AdbPacket {
            val hdr = readExactly(24)
            val bb = ByteBuffer.wrap(hdr).order(ByteOrder.LITTLE_ENDIAN)
            val cmd = bb.int
            val arg0 = bb.int
            val arg1 = bb.int
            val dataLen = bb.int
            val crc = bb.int
            val magic = bb.int
            if (magic != (cmd xor -1)) {
                throw IOException("Gói tin ADB không hợp lệ (bad magic)")
            }
            if (dataLen < 0 || dataLen > MAX_REQUEST_DATA * 2) {
                throw IOException("Kích thước gói tin ADB bất thường: $dataLen")
            }
            val payload = if (dataLen > 0) readExactly(dataLen) else ByteArray(0)
            return AdbPacket(cmd, arg0, arg1, crc, payload)
        }

        private fun readExactly(len: Int): ByteArray {
            val buf = ByteArray(len)
            var off = 0
            while (off < len) {
                val r = input.read(buf, off, len - off)
                if (r < 0) throw EOFException("Kết nối ADB với TV bị ngắt")
                off += r
            }
            return buf
        }

        override fun close() {
            runCatching { socket.close() }
        }
    }

    private data class AdbPacket(
        val command: Int,
        val arg0: Int,
        val arg1: Int,
        val checksum: Int,
        val payload: ByteArray
    )

    companion object {
        const val DEFAULT_ADB_PORT = 5555
        const val TARGET_PACKAGE = "com.phyll.filmhub"
        const val TARGET_ACTIVITY = "com.phyll.filmhub/.MainActivity"

        const val A_CNXN = 0x4e584e43
        const val A_AUTH = 0x48545541
        const val A_OPEN = 0x4e45504f
        const val A_OKAY = 0x59414b4f
        const val A_CLSE = 0x45534c43
        const val A_WRTE = 0x45545257

        const val ADB_VERSION = 0x01000000
        const val DEFAULT_MAX_DATA = 64 * 1024
        const val MAX_REQUEST_DATA = 256 * 1024

        const val ADB_AUTH_TOKEN = 1
        const val ADB_AUTH_SIGNATURE = 2
        const val ADB_AUTH_RSAPUBLICKEY = 3

        private val SHA1_DIGEST_INFO_PREFIX = byteArrayOf(
            0x30, 0x21, 0x30, 0x09, 0x06, 0x05, 0x2b, 0x0e,
            0x03, 0x02, 0x1a, 0x05, 0x00, 0x04, 0x14
        )

        fun computeAdbChecksum(payload: ByteArray): Int {
            var sum = 0
            for (b in payload) {
                sum += (b.toInt() and 0xFF)
            }
            return sum
        }

        fun signAdbToken(token: ByteArray, privateKey: java.security.PrivateKey): ByteArray {
            val digestInfo = ByteArray(SHA1_DIGEST_INFO_PREFIX.size + token.size)
            System.arraycopy(SHA1_DIGEST_INFO_PREFIX, 0, digestInfo, 0, SHA1_DIGEST_INFO_PREFIX.size)
            System.arraycopy(token, 0, digestInfo, SHA1_DIGEST_INFO_PREFIX.size, token.size)
            val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
            cipher.init(Cipher.ENCRYPT_MODE, privateKey)
            return cipher.doFinal(digestInfo)
        }

        /**
         * Encodes a 2048-bit RSA public key in AOSP's 524-byte `RSAPublicKey` binary layout,
         * Base64-encoded with a descriptive identifier + trailing NUL byte.
         */
        fun encodeAdbPublicKeyString(publicKey: RSAPublicKey): ByteArray {
            val structBytes = encodeAdbPublicKeyStruct(publicKey)
            val b64 = encodeBase64NoWrap(structBytes)
            return "$b64 phyll-android-controller\u0000".toByteArray(Charsets.US_ASCII)
        }

        private fun encodeBase64NoWrap(input: ByteArray): String {
            val table = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
            val out = StringBuilder(((input.size + 2) / 3) * 4)
            var i = 0
            while (i < input.size) {
                val b0 = input[i++].toInt() and 0xFF
                val hasB1 = i < input.size
                val b1 = if (hasB1) input[i++].toInt() and 0xFF else 0
                val hasB2 = i < input.size
                val b2 = if (hasB2) input[i++].toInt() and 0xFF else 0

                out.append(table[b0 ushr 2])
                out.append(table[((b0 and 0x03) shl 4) or (b1 ushr 4)])
                out.append(if (hasB1) table[((b1 and 0x0F) shl 2) or (b2 ushr 6)] else '=')
                out.append(if (hasB2) table[b2 and 0x3F] else '=')
            }
            return out.toString()
        }

        fun encodeAdbPublicKeyStruct(publicKey: RSAPublicKey): ByteArray {
            val modBytesLen = 256
            val words = modBytesLen / 4
            val modulus = publicKey.modulus
            val twoPow32 = BigInteger.ONE.shiftLeft(32)
            val n0inv = twoPow32.subtract(modulus.mod(twoPow32).modInverse(twoPow32)).toInt()
            val r = BigInteger.ONE.shiftLeft(2048)
            val rr = r.multiply(r).mod(modulus)

            val buf = ByteBuffer.allocate(524).order(ByteOrder.LITTLE_ENDIAN)
            buf.putInt(words)
            buf.putInt(n0inv)
            buf.put(bigIntToLittleEndianBytes(modulus, modBytesLen))
            buf.put(bigIntToLittleEndianBytes(rr, modBytesLen))
            buf.putInt(publicKey.publicExponent.toInt())
            return buf.array()
        }

        private fun bigIntToLittleEndianBytes(value: BigInteger, size: Int): ByteArray {
            val be = value.toByteArray()
            val le = ByteArray(size)
            for (i in 0 until size) {
                val srcIdx = be.size - 1 - i
                le[i] = if (srcIdx >= 0) be[srcIdx] else 0
            }
            return le
        }

        fun parseHardwareInfoOutput(raw: String): AdbTvHardwareInfo {
            var model = ""
            var brand = ""
            var pkgInstalled = false
            var version = ""
            for (line in raw.lineSequence()) {
                val t = line.trim()
                when {
                    t.startsWith("MODEL=") -> model = t.removePrefix("MODEL=").trim()
                    t.startsWith("BRAND=") -> brand = t.removePrefix("BRAND=").trim()
                    t.startsWith("PKG=") -> pkgInstalled = t.removePrefix("PKG=").trim().isNotEmpty()
                    t.startsWith("VER=") -> {
                        val v = t.removePrefix("VER=").substringAfter("versionName=", "").trim()
                        if (v.isNotEmpty()) version = v
                    }
                }
            }
            return AdbTvHardwareInfo(
                model = model,
                brand = brand,
                packageInstalled = pkgInstalled,
                appVersion = version
            )
        }
    }
}
