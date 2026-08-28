package top.boluofan.musictv.data.storage

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import top.boluofan.musictv.data.model.MusicInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

val Context.resumeSnapshotDataStore by preferencesDataStore(name = "resume_snapshot")

/** 续播快照：上次播放的队列、当前曲下标与播放进度（毫秒） */
data class ResumeSnapshot(
    val queue: List<MusicInfo>,
    val index: Int,
    val positionMs: Long,
    val savedAt: Long = 0L
)

/**
 * 续播快照持久化。独立 DataStore 文件，避免大队列 JSON 挤占主设置文件；
 * 队列超长时只保留尾部（听歌场景越靠后越可能是用户想续听的部分）。
 */
@Singleton
class ResumeSnapshotStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val gson = Gson()

    val snapshot: Flow<ResumeSnapshot?> = context.resumeSnapshotDataStore.data.map { prefs ->
        prefs[SNAPSHOT_KEY]?.let { json ->
            runCatching { gson.fromJson(json, ResumeSnapshot::class.java) }.getOrNull()
        }
    }

    suspend fun save(snapshot: ResumeSnapshot) {
        val trimmed = if (snapshot.queue.size > MAX_QUEUE) {
            val tail = snapshot.queue.takeLast(MAX_QUEUE)
            snapshot.copy(queue = tail, index = snapshot.index - (snapshot.queue.size - MAX_QUEUE))
        } else {
            snapshot
        }
        if (trimmed.queue.isEmpty() || trimmed.index !in trimmed.queue.indices) return
        context.resumeSnapshotDataStore.edit { it[SNAPSHOT_KEY] = gson.toJson(trimmed) }
    }

    companion object {
        private val SNAPSHOT_KEY = stringPreferencesKey("resume_snapshot")
        private const val MAX_QUEUE = 500
    }
}
