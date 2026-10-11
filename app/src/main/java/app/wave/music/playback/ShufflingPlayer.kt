package app.wave.music.playback

import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ShuffleOrder
import kotlin.random.Random

/**
 * Shuffle the way people expect it: turning shuffle on (or starting a shuffled queue) keeps the
 * current song first and shuffles everything after it, so the whole queue is still ahead of you.
 * Songs added while shuffled land after the current song: "Play next" right after it, anything
 * else somewhere later. ExoPlayer on its own can place the current song anywhere in the shuffled
 * order, which left "Up next" empty and stopped playback early.
 */
class ShufflingPlayer(private val exo: ExoPlayer) : ForwardingPlayer(exo) {

    /** Window indices in shuffled play order. */
    private fun shuffledOrder(): MutableList<Int> {
        val timeline = exo.currentTimeline
        val order = mutableListOf<Int>()
        if (timeline.isEmpty) return order
        var i = timeline.getFirstWindowIndex(true)
        while (i != C.INDEX_UNSET && order.size < timeline.windowCount) {
            order += i
            i = timeline.getNextWindowIndex(i, REPEAT_MODE_OFF, true)
        }
        return order
    }

    private fun apply(order: List<Int>) {
        if (order.size != exo.mediaItemCount) return
        exo.setShuffleOrder(ShuffleOrder.DefaultShuffleOrder(order.toIntArray(), Random.nextLong()))
    }

    /** The current song first, then everything else in a fresh random order. */
    private fun reshuffleFromCurrent() {
        val count = exo.mediaItemCount
        if (count == 0) return
        val current = exo.currentMediaItemIndex.coerceIn(0, count - 1)
        apply(listOf(current) + (0 until count).filter { it != current }.shuffled())
    }

    /** Runs an insert, then fits the new items into the existing shuffled order. */
    private inline fun inserting(index: Int, count: Int, add: () -> Unit) {
        val before = shuffledOrder()
        val currentBefore = exo.currentMediaItemIndex
        add()
        if (!exo.shuffleModeEnabled || count == 0) return
        val order = before.map { if (it >= index) it + count else it }.toMutableList()
        val anchor = order.indexOf(exo.currentMediaItemIndex).coerceAtLeast(0)
        val inserted = (index until index + count).toList()
        if (index == currentBefore + 1) {
            // "Play next": right after the current song.
            order.addAll(anchor + 1, inserted)
        } else {
            for (item in inserted.shuffled()) order.add(Random.nextInt(anchor + 1, order.size + 1), item)
        }
        apply(order)
    }

    override fun setShuffleModeEnabled(shuffleModeEnabled: Boolean) {
        if (shuffleModeEnabled && !exo.shuffleModeEnabled) reshuffleFromCurrent()
        super.setShuffleModeEnabled(shuffleModeEnabled)
    }

    override fun setMediaItems(mediaItems: MutableList<MediaItem>) {
        super.setMediaItems(mediaItems)
        if (exo.shuffleModeEnabled) reshuffleFromCurrent()
    }

    override fun setMediaItems(mediaItems: MutableList<MediaItem>, resetPosition: Boolean) {
        super.setMediaItems(mediaItems, resetPosition)
        if (exo.shuffleModeEnabled) reshuffleFromCurrent()
    }

    override fun setMediaItems(mediaItems: MutableList<MediaItem>, startIndex: Int, startPositionMs: Long) {
        super.setMediaItems(mediaItems, startIndex, startPositionMs)
        if (exo.shuffleModeEnabled) reshuffleFromCurrent()
    }

    override fun setMediaItem(mediaItem: MediaItem) {
        super.setMediaItem(mediaItem)
        if (exo.shuffleModeEnabled) reshuffleFromCurrent()
    }

    override fun setMediaItem(mediaItem: MediaItem, startPositionMs: Long) {
        super.setMediaItem(mediaItem, startPositionMs)
        if (exo.shuffleModeEnabled) reshuffleFromCurrent()
    }

    override fun setMediaItem(mediaItem: MediaItem, resetPosition: Boolean) {
        super.setMediaItem(mediaItem, resetPosition)
        if (exo.shuffleModeEnabled) reshuffleFromCurrent()
    }

    override fun addMediaItem(mediaItem: MediaItem) {
        val index = exo.mediaItemCount
        inserting(index, 1) { super.addMediaItem(mediaItem) }
    }

    override fun addMediaItem(index: Int, mediaItem: MediaItem) {
        val at = index.coerceIn(0, exo.mediaItemCount)
        inserting(at, 1) { super.addMediaItem(index, mediaItem) }
    }

    override fun addMediaItems(mediaItems: MutableList<MediaItem>) {
        val index = exo.mediaItemCount
        inserting(index, mediaItems.size) { super.addMediaItems(mediaItems) }
    }

    override fun addMediaItems(index: Int, mediaItems: MutableList<MediaItem>) {
        val at = index.coerceIn(0, exo.mediaItemCount)
        inserting(at, mediaItems.size) { super.addMediaItems(index, mediaItems) }
    }
}
