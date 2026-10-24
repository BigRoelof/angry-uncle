package nl.angryuncle.heads

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * A head the user made, ready to draw. [eyes] are fractions of the image, from face detection on the calm photo.
 * [yell] is their recorded angry yell, if they made one.
 */
class CustomHead(
    val id: String,
    val name: String,
    val calm: ImageBitmap,
    val angry: ImageBitmap?,
    val eyes: Pair<Offset, Offset>?,
    val yell: File? = null,
)

/**
 * Heads live in app-private storage only: filesDir/heads/<id>/{calm.png,angry.png,yell.m4a} plus an index.json.
 * Nothing leaves the phone.
 */
object HeadStore {
    private const val YellFile = "yell.m4a"

    private fun root(context: Context) = File(context.filesDir, "heads")
    private fun index(context: Context) = File(root(context), "index.json")

    private class Entry(val id: String, val name: String, val hasAngry: Boolean, val eyes: Pair<Offset, Offset>?)

    private fun readIndex(context: Context): List<Entry> {
        val f = index(context)
        if (!f.exists()) return emptyList()
        val arr = runCatching { JSONObject(f.readText()).getJSONArray("heads") }.getOrNull() ?: return emptyList()
        return List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            val eyes = o.optJSONArray("eyes")?.let { e ->
                Offset(e.getDouble(0).toFloat(), e.getDouble(1).toFloat()) to
                    Offset(e.getDouble(2).toFloat(), e.getDouble(3).toFloat())
            }
            Entry(o.getString("id"), o.optString("name"), o.optBoolean("angry"), eyes)
        }
    }

    private fun writeIndex(context: Context, entries: List<Entry>) {
        val arr = JSONArray()
        for (e in entries) {
            arr.put(
                JSONObject()
                    .put("id", e.id)
                    .put("name", e.name)
                    .put("angry", e.hasAngry)
                    .apply {
                        e.eyes?.let { (l, r) -> put("eyes", JSONArray(listOf(l.x, l.y, r.x, r.y).map { it.toDouble() })) }
                    },
            )
        }
        root(context).mkdirs()
        // Write-then-rename, so a crash mid-write cannot wipe every head.
        val tmp = File(root(context), "index.json.tmp")
        tmp.writeText(JSONObject().put("heads", arr).toString())
        tmp.renameTo(index(context))
    }

    suspend fun loadAll(context: Context): List<CustomHead> = withContext(Dispatchers.IO) {
        readIndex(context).mapNotNull { e ->
            val dir = File(root(context), e.id)
            val calm = BitmapFactory.decodeFile(File(dir, "calm.png").path) ?: return@mapNotNull null
            val angry = if (e.hasAngry) BitmapFactory.decodeFile(File(dir, "angry.png").path) else null
            val yell = File(dir, YellFile).takeIf { it.exists() }
            CustomHead(e.id, e.name, calm.asImageBitmap(), angry?.asImageBitmap(), e.eyes, yell)
        }
    }

    /** Saves a new head. [yell] is a recording to copy into the head's folder, if one was made. */
    suspend fun save(context: Context, name: String, calm: Bitmap, angry: Bitmap?, eyes: Pair<Offset, Offset>?, yell: File?) =
        withContext(Dispatchers.IO) {
            val id = UUID.randomUUID().toString()
            val dir = File(root(context), id).apply { mkdirs() }
            File(dir, "calm.png").outputStream().use { calm.compress(Bitmap.CompressFormat.PNG, 100, it) }
            angry?.let { a -> File(dir, "angry.png").outputStream().use { a.compress(Bitmap.CompressFormat.PNG, 100, it) } }
            yell?.copyTo(File(dir, YellFile), overwrite = true)
            writeIndex(context, readIndex(context) + Entry(id, name.trim(), angry != null, eyes))
        }

    suspend fun delete(context: Context, id: String) = withContext(Dispatchers.IO) {
        writeIndex(context, readIndex(context).filter { it.id != id })
        File(root(context), id).deleteRecursively()
    }
}
