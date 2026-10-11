package app.wave.music.playback

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.TransferListener
import timber.log.Timber
import java.io.IOException
import java.io.InterruptedIOException

/**
 * Reads a YouTube audio stream all the way to its last byte.
 *
 * YouTube sometimes cuts a download short: a dropped connection, a response that simply ends
 * early, or a stream that only answers bounded range requests. A plain HTTP read treats any of
 * those as the end of the song, so music stopped before it finished (and the cache then
 * remembered the song as shorter than it is). Because Wave knows each stream's true size, this
 * keeps going instead: it resumes from the exact byte it stopped at, in chunks when the stream
 * requires them, until the whole song has arrived.
 *
 * The true size and chunk size arrive as the [LENGTH] and [CHUNK] request headers, which are
 * never sent upstream. Requests without them pass straight through.
 */
class StreamDataSource(private val upstream: DataSource) : DataSource {
    class Factory(private val upstream: DataSource.Factory) : DataSource.Factory {
        override fun createDataSource(): DataSource = StreamDataSource(upstream.createDataSource())
    }

    private var spec: DataSpec? = null
    private var chunkSize = 0L
    /** Absolute position of the next byte to read. */
    private var next = 0L
    /** Absolute end of what was asked for (exclusive), when known. */
    private var end = UNSET
    /** Bytes left in the open request, or [UNSET] for an open-ended one. */
    private var pieceLeft = UNSET
    private var pieceOpen = false
    /** With no known size: the server signalled the file's end. */
    private var finished = false
    /** Resumes in a row without getting any data. */
    private var stalls = 0

    override fun addTransferListener(transferListener: TransferListener) = upstream.addTransferListener(transferListener)

    override fun open(dataSpec: DataSpec): Long {
        val headers = dataSpec.httpRequestHeaders
        val chunk = headers[CHUNK]?.toLongOrNull() ?: 0L
        val total = headers[LENGTH]?.toLongOrNull() ?: -1L
        if (chunk <= 0L && total <= 0L) {
            spec = null
            return upstream.open(dataSpec)
        }
        val clean = dataSpec.withRequestHeaders(headers - CHUNK - LENGTH)
        spec = clean
        chunkSize = chunk
        next = clean.position
        finished = false
        stalls = 0
        end = when {
            clean.length != UNSET && total > 0 -> minOf(clean.position + clean.length, total)
            clean.length != UNSET -> clean.position + clean.length
            total > 0 -> total
            else -> UNSET
        }
        openPiece()
        if (end == UNSET) contentRangeTotal()?.let { end = it }
        return if (end == UNSET) UNSET else (end - clean.position).coerceAtLeast(0L)
    }

    private fun openPiece() {
        val s = spec ?: return
        val remaining = if (end == UNSET) UNSET else end - next
        if (remaining == 0L) {
            finished = true
            return
        }
        val length = when {
            chunkSize > 0L && remaining != UNSET -> minOf(chunkSize, remaining)
            chunkSize > 0L -> chunkSize
            else -> remaining
        }
        val opened = try {
            upstream.open(s.subrange(next - s.position, length))
        } catch (e: HttpDataSource.InvalidResponseCodeException) {
            // 416: asked for bytes past the end of the file, so it's all here.
            if (e.responseCode != 416) throw e
            closeQuietly()
            finished = true
            return
        }
        pieceOpen = true
        pieceLeft = when {
            length == UNSET -> opened
            opened == UNSET -> length
            else -> minOf(opened, length)
        }
    }

    /** The whole file's size, from a "Content-Range: bytes a-b/total" response header. */
    private fun contentRangeTotal(): Long? = upstream.responseHeaders.entries
        .firstOrNull { it.key.equals("Content-Range", ignoreCase = true) }
        ?.value?.firstOrNull()
        ?.substringAfterLast('/')
        ?.trim()
        ?.toLongOrNull()

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (spec == null) return upstream.read(buffer, offset, length)
        if (length == 0) return 0
        while (true) {
            if (end != UNSET && next >= end) return C.RESULT_END_OF_INPUT
            if (finished) return C.RESULT_END_OF_INPUT
            if (!pieceOpen || pieceLeft == 0L) {
                if (pieceOpen) closeQuietly()
                openPiece()
                if (!pieceOpen) return C.RESULT_END_OF_INPUT
                continue
            }
            val want = if (pieceLeft == UNSET) length else minOf(length.toLong(), pieceLeft).toInt()
            val read = try {
                upstream.read(buffer, offset, want)
            } catch (e: IOException) {
                // The player cancelling a load isn't a dropped connection.
                if (e is InterruptedIOException || end == UNSET || ++stalls > MAX_STALLS) throw e
                Timber.w(e, "Stream dropped at byte %d of %d, resuming", next, end)
                closeQuietly()
                continue
            }
            if (read == C.RESULT_END_OF_INPUT) {
                if (end != UNSET && next < end) {
                    // Ended early: pick up from this exact byte.
                    if (++stalls > MAX_STALLS) throw IOException("Stream keeps ending at byte $next of $end")
                    Timber.w("Stream ended early at byte %d of %d, resuming", next, end)
                    closeQuietly()
                    continue
                }
                // No known size: a bounded chunk coming back short is the end of the file.
                if (chunkSize > 0L && pieceLeft != 0L) finished = true
                if (chunkSize <= 0L) finished = true
                closeQuietly()
                continue
            }
            next += read
            if (pieceLeft != UNSET) pieceLeft -= read
            stalls = 0
            return read
        }
    }

    private fun closeQuietly() {
        pieceOpen = false
        runCatching { upstream.close() }
    }

    override fun getUri(): Uri? = upstream.uri

    override fun getResponseHeaders(): Map<String, List<String>> = upstream.responseHeaders

    override fun close() {
        spec = null
        pieceOpen = false
        upstream.close()
    }

    companion object {
        const val CHUNK = "X-Wave-Chunk"
        const val LENGTH = "X-Wave-Length"
        private const val UNSET = C.LENGTH_UNSET.toLong()
        private const val MAX_STALLS = 6
    }
}
