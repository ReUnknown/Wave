package app.wave.music.playback

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Live loudness and beats of what's playing, for "Move with the music". */
object BeatBus {
    /** Written by the platform's audio measuring. */
    val levelSink = MutableStateFlow(0f)
    /** 0..1 loudness relative to the song's recent average. */
    val level: StateFlow<Float> = levelSink.asStateFlow()

    /** Wave is on screen. Off screen (or with "Move with the music" off) nothing is measured. */
    @Volatile var visible = false
        set(value) {
            field = value
            _visibleFlow.value = value
        }
    private val _visibleFlow = MutableStateFlow(false)
    /** Same as [visible], to react to (the service switches low-power playback on it). */
    val visibleFlow: StateFlow<Boolean> = _visibleFlow.asStateFlow()

    val beatSink = MutableStateFlow(0L)
    /** Changes (to the time it happened) on every detected beat. */
    val beat: StateFlow<Long> = beatSink.asStateFlow()
}
