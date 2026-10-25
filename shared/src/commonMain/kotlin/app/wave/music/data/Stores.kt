package app.wave.music.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import app.wave.music.platform.WavePlatform
import okio.Path.Companion.toPath
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Wave's saved data. Preferences live where Android's `preferencesDataStore` has always kept them
 * (`<data>/datastore/<name>.preferences_pb`), so updating to the shared app keeps everything.
 */
object Stores {
    private val open = ConcurrentHashMap<String, DataStore<Preferences>>()

    fun prefs(name: String): DataStore<Preferences> = open.getOrPut(name) {
        PreferenceDataStoreFactory.createWithPath(
            produceFile = { File(WavePlatform.current.dataDir, "datastore/$name.preferences_pb").absolutePath.toPath() },
        )
    }

    fun file(name: String): File = File(WavePlatform.current.dataDir, name)
}

/** A small least-recently-used cache (Android's LruCache isn't available on computers). */
class Lru<K : Any, V : Any>(private val max: Int) {
    private val map = object : LinkedHashMap<K, V>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?) = size > max
    }

    @Synchronized fun get(key: K): V? = map[key]
    @Synchronized fun put(key: K, value: V) {
        map[key] = value
    }
    @Synchronized fun remove(key: K) {
        map.remove(key)
    }
    @Synchronized fun evictAll() = map.clear()
}
