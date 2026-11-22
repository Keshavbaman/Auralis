package app.auralis.cast

import android.content.Context
import android.net.*
import android.os.PowerManager
import app.auralis.data.LibraryBook
import java.io.*
import java.net.*
import java.security.SecureRandom
import java.util.concurrent.*

/** A bounded LAN-only, token-protected read server. Never copies or buffers an entire book. */
class LocalCastServer(private val context: Context) {
    data class Asset(val uri: Uri, val length: Long, val mime: String)

    private val assets = ConcurrentHashMap<String, Asset>()
    private val uriKeys = ConcurrentHashMap<String, String>()
    private val sockets = ConcurrentHashMap.newKeySet<Socket>()
    private val workers =
        ThreadPoolExecutor(
            2,
            4,
            30,
            TimeUnit.SECONDS,
            ArrayBlockingQueue(8),
            ThreadPoolExecutor.AbortPolicy(),
        )
    @Volatile private var server: ServerSocket? = null
    @Volatile private var host: String? = null
    @Volatile private var secret = ""
    private val wake =
        (context.getSystemService(Context.POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Auralis:CastAudio")
            .apply { setReferenceCounted(false) }

    private val connectivity =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    @Volatile private var wifiAddress: Inet4Address? = null
    private val networkCallback =
        object : ConnectivityManager.NetworkCallback() {
            override fun onLinkPropertiesChanged(network: Network, properties: LinkProperties) {
                val next =
                    properties.linkAddresses
                        .map { it.address }
                        .filterIsInstance<Inet4Address>()
                        .firstOrNull { !it.isLoopbackAddress }
                if (host != null && host != next?.hostAddress) stop()
                wifiAddress = next
            }

            override fun onLost(network: Network) {
                wifiAddress = null
                stop()
            }
        }

    init {
        connectivity.registerNetworkCallback(
            NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build(),
            networkCallback,
        )
    }

    /** Run off the main thread during the ordinary book-load transaction. */
    fun register(book: LibraryBook) {
        val next = mutableMapOf<String, Asset>()
        val keys = mutableMapOf<String, String>()
        for (file in book.files) {
            context.contentResolver.openAssetFileDescriptor(Uri.parse(file.uri), "r")!!.use { fd ->
                val length = fd.length.takeIf { it >= 0 } ?: fd.parcelFileDescriptor.statSize
                val header = ByteArray(64)
                fd.createInputStream().use { it.read(header) }
                val mime =
                    when {
                        String(header, 4, 4, Charsets.US_ASCII) == "ftyp" -> "audio/mp4"
                        String(header, 0, 4, Charsets.US_ASCII) == "OggS" -> "audio/ogg"
                        String(header, 0, 4, Charsets.US_ASCII) == "fLaC" -> "audio/flac"
                        String(header, 0, 4, Charsets.US_ASCII) == "RIFF" -> "audio/wav"
                        else ->
                            context.contentResolver.getType(Uri.parse(file.uri))?.takeIf {
                                it.startsWith("audio/")
                            } ?: "audio/mpeg"
                    }
                val key = "audio-${file.id}"
                next[key] = Asset(Uri.parse(file.uri), length, mime)
                keys[file.uri] = key
            }
        }
        book.book.artwork?.let { path ->
            val file = File(path)
            if (file.isFile) {
                val key = "art-${book.book.id}"
                next[key] = Asset(Uri.fromFile(file), file.length(), "image/jpeg")
                keys[Uri.fromFile(file).toString()] = key
            }
        }
        assets.clear()
        assets.putAll(next)
        uriKeys.clear()
        uriKeys.putAll(keys)
    }

    fun mime(uri: Uri) = assets[uriKeys[uri.toString()]]?.mime ?: "audio/mpeg"

    @Synchronized
    fun url(uri: Uri): Uri {
        val key =
            uriKeys[uri.toString()]
                ?: throw IOException("Select the audiobook again before casting")
        val asset = assets[key] ?: throw IOException("Audio is no longer available")
        require(asset.length > 0) { "This storage provider cannot supply ranged audio" }
        if (server == null) start()
        return Uri.parse("http://$host:${server!!.localPort}/$secret/$key")
    }

    private fun start() {
        val active = connectivity.activeNetwork
        val fallback =
            active
                ?.takeIf {
                    connectivity
                        .getNetworkCapabilities(it)
                        ?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
                }
                ?.let { connectivity.getLinkProperties(it) }
                ?.linkAddresses
                ?.map { it.address }
                ?.filterIsInstance<Inet4Address>()
                ?.firstOrNull { !it.isLoopbackAddress }
        val address =
            wifiAddress
                ?: fallback
                ?: throw IOException("Connect phone and speaker to the same Wi-Fi with IPv4")
        val bound =
            ServerSocket().apply {
                reuseAddress = true
                bind(InetSocketAddress(address, 0), 8)
            }
        secret =
            ByteArray(32)
                .also { SecureRandom().nextBytes(it) }
                .joinToString("") { "%02x".format(it) }
        host = address.hostAddress
        server = bound
        Thread(
                {
                    while (!bound.isClosed) {
                        val socket =
                            try {
                                bound.accept()
                            } catch (_: IOException) {
                                break
                            }
                        sockets += socket
                        try {
                            workers.execute {
                                try {
                                    serve(socket)
                                } finally {
                                    sockets -= socket
                                    runCatching { socket.close() }
                                }
                            }
                        } catch (_: RejectedExecutionException) {
                            sockets -= socket
                            socket.close()
                        }
                    }
                },
                "auralis-cast-accept",
            )
            .apply {
                isDaemon = true
                start()
            }
    }

    private fun line(input: InputStream): String {
        val data = ByteArrayOutputStream()
        while (data.size() < 8192) {
            val b = input.read()
            if (b < 0) throw EOFException()
            if (b == 10) return data.toString("US-ASCII").trimEnd('\r')
            data.write(b)
        }
        throw IOException("Oversized HTTP header")
    }

    private fun serve(socket: Socket) {
        try {
            socket.soTimeout = 15000
            val input = BufferedInputStream(socket.getInputStream())
            val out = BufferedOutputStream(socket.getOutputStream())
            val request = line(input).split(' ')
            if (request.size != 3) return
            val method = request[0]
            val path = request[1]
            val headers = mutableMapOf<String, String>()
            var count = 0
            while (true) {
                val h = line(input)
                if (h.isEmpty()) break
                if (++count > 40) throw IOException("Too many headers")
                val colon = h.indexOf(':')
                if (colon > 0)
                    headers[h.substring(0, colon).lowercase()] = h.substring(colon + 1).trim()
            }
            fun respond(code: String, length: Long = 0, extra: String = "") {
                out.write(
                    ("HTTP/1.1 $code\r\nConnection: close\r\nContent-Length: $length\r\nAccess-Control-Allow-Origin: *\r\nAccess-Control-Allow-Headers: Range\r\nAccess-Control-Allow-Methods: GET, HEAD, OPTIONS\r\nCache-Control: no-store\r\nAccess-Control-Expose-Headers: Content-Range,Accept-Ranges,Content-Length\r\n$extra\r\n")
                        .toByteArray(Charsets.US_ASCII)
                )
                out.flush()
            }
            val prefix = "/$secret/"
            if (!path.startsWith(prefix) || path.contains('?') || path.contains("..")) {
                respond("404 Not Found")
                return
            }
            val asset =
                assets[path.removePrefix(prefix)]
                    ?: run {
                        respond("404 Not Found")
                        return
                    }
            if (method == "OPTIONS") {
                respond("204 No Content")
                return
            }
            if (method != "GET" && method != "HEAD") {
                respond("405 Method Not Allowed")
                return
            }
            val range =
                parseByteRange(headers["range"], asset.length)
                    ?: run {
                        respond(
                            "416 Range Not Satisfiable",
                            extra = "Content-Range: bytes */${asset.length}\r\n",
                        )
                        return
                    }
            val stream =
                if (asset.uri.scheme == "file") FileInputStream(asset.uri.path!!)
                else
                    context.contentResolver
                        .openAssetFileDescriptor(asset.uri, "r")!!
                        .createInputStream()
            stream.use { source ->
                if (range.start > 0) {
                    try {
                        source.channel.position(source.channel.position() + range.start)
                    } catch (e: IOException) {
                        respond("503 Service Unavailable")
                        return
                    }
                }
                if (app.auralis.BuildConfig.DEBUG)
                    android.util.Log.d(
                        "AuralisCast",
                        "Serve ${asset.mime} bytes=${range.start}-${range.end}",
                    )
                val partial = headers.containsKey("range")
                respond(
                    if (partial) "206 Partial Content" else "200 OK",
                    range.length,
                    "Content-Type: ${asset.mime}\r\nAccept-Ranges: bytes\r\n" +
                        if (partial)
                            "Content-Range: bytes ${range.start}-${range.end}/${asset.length}\r\n"
                        else "",
                )
                if (method == "HEAD") return
                val buffer = ByteArray(64 * 1024)
                var left = range.length
                while (left > 0) {
                    val n = source.read(buffer, 0, minOf(buffer.size.toLong(), left).toInt())
                    if (n < 0) break
                    out.write(buffer, 0, n)
                    left -= n
                }
                out.flush()
            }
        } catch (_: Exception) {
            /* Socket closes on disconnect, malformed input or unavailable storage. */
        }
    }

    fun power(playing: Boolean) {
        if (playing && !wake.isHeld) wake.acquire(30 * 60 * 1000L)
        else if (!playing && wake.isHeld) wake.release()
    }

    @Synchronized
    fun stop() {
        runCatching { server?.close() }
        server = null
        host = null
        secret = ""
        sockets.forEach { runCatching { it.close() } }
        sockets.clear()
        power(false)
    }

    fun release() {
        runCatching { connectivity.unregisterNetworkCallback(networkCallback) }
        stop()
        workers.shutdownNow()
        assets.clear()
        uriKeys.clear()
    }
}
