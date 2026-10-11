package app.wave.music.playback

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.TransferListener

/**
 * Some YouTube streams only answer bounded range requests. This reads such a stream as one
 * continuous file by requesting it chunk after chunk, so the player never sees a chunk's end as
 * the song's end (which made long songs stop early). Streams that don't need it pass straight
 * through. The chunk size arrives as the [HEADER] request header, which is never sent upstream.
 */
class ChunkedDataSource(private val upstream: DataSource) : DataSource {
    class Factory(private val upstream: DataSource.Factory) : DataSource.Factory {
        override fun createDataSource(): DataSource = ChunkedDataSource(upstream.createDataSource())
    }

    private var spec: DataSpec? = null
    private var chunkSize = 0L
    /** Absolute position of the next byte to read. */
    private var next = 0L
    /** Absolute end of what was asked for (exclusive), once known. */
    private var end = UNSET
    /** Bytes still to read from the open chunk. */
    private var chunkLeft = 0L
    /** The server ran out before the chunk did: this chunk is the end of the file. */
    private var lastChunk = false

    override fun addTransferListener(transferListener: TransferListener) = upstream.addTransferListener(transferListener)

    override fun open(dataSpec: DataSpec): Long {
        val size = dataSpec.httpRequestHeaders[HEADER]?.toLongOrNull() ?: 0L
        if (size <= 0L) {
            spec = null
            return upstream.open(dataSpec)
        }
        val clean = dataSpec.withRequestHeaders(dataSpec.httpRequestHeaders - HEADER)
        spec = clean
        chunkSize = size
        next = clean.position
        end = if (clean.length != UNSET) clean.position + clean.length else UNSET
        lastChunk = false
        openChunk()
        totalLength()?.let { total -> end = if (end == UNSET) total else minOf(end, total) }
        return if (end == UNSET) UNSET else (end - clean.position).coerceAtLeast(0L)
    }

    private fun openChunk() {
        val s = spec ?: return
        val length = if (end == UNSET) chunkSize else minOf(chunkSize, end - next)
        if (length <= 0L) {
            chunkLeft = 0L
            lastChunk = true
            return
        }
        val opened = try {
            upstream.open(s.subrange(next - s.position, length))
        } catch (e: HttpDataSource.InvalidResponseCodeException) {
            // 416: asked for bytes past the end of the file, so we're done.
            if (e.responseCode != 416) throw e
            upstream.close()
            chunkLeft = 0L
            lastChunk = true
            return
        }
        chunkLeft = if (opened == UNSET) length else minOf(opened, length)
        lastChunk = chunkLeft < length
    }

    /** The whole file's size, from a "Content-Range: bytes a-b/total" response header. */
    private fun totalLength(): Long? = upstream.responseHeaders.entries
        .firstOrNull { it.key.equals("Content-Range", ignoreCase = true) }
        ?.value?.firstOrNull()
        ?.substringAfterLast('/')
        ?.trim()
        ?.toLongOrNull()

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (spec == null) return upstream.read(buffer, offset, length)
        if (length == 0) return 0
        if (end != UNSET && next >= end) return C.RESULT_END_OF_INPUT
        if (chunkLeft == 0L) {
            if (lastChunk) return C.RESULT_END_OF_INPUT
            upstream.close()
            openChunk()
            if (chunkLeft == 0L) return C.RESULT_END_OF_INPUT
        }
        val read = upstream.read(buffer, offset, minOf(length.toLong(), chunkLeft).toInt())
        if (read == C.RESULT_END_OF_INPUT) {
            chunkLeft = 0L
            lastChunk = true
            return C.RESULT_END_OF_INPUT
        }
        next += read
        chunkLeft -= read
        return read
    }

    override fun getUri(): Uri? = upstream.uri

    override fun getResponseHeaders(): Map<String, List<String>> = upstream.responseHeaders

    override fun close() {
        spec = null
        upstream.close()
    }

    companion object {
        const val HEADER = "X-Wave-Chunk"
        private const val UNSET = C.LENGTH_UNSET.toLong()
    }
}
