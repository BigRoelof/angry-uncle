package nl.angryuncle.heads

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import java.io.File

/** Recordings longer than this are cut off: a yell, not a speech. */
private const val MaxYellMs = 4000

/** Anything shorter was a slip of the finger, not a yell. */
private const val MinYellMs = 300

/**
 * Hold-to-record for a head's angry yell, with a play-back button.
 * A good take replaces [file] and calls [onRecorded]; a slip of the finger leaves the previous take alone.
 */
@Composable
fun YellRecorder(file: File, hasRecording: Boolean, onRecorded: () -> Unit) {
    val context = LocalContext.current
    val currentOnRecorded by rememberUpdatedState(onRecorded)
    var recording by remember { mutableStateOf(false) }
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    val recorder = remember { YellRecording(context) }
    DisposableEffect(Unit) { onDispose { recorder.release() } }

    Column(Modifier.fillMaxWidth()) {
        Text("Angry yell (optional)", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
        Text(
            "Hold the button and let them yell. It plays when their head is the angry one.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            val color = MaterialTheme.colorScheme.primary
            Box(
                Modifier
                    .weight(1f)
                    .height(52.dp)
                    .background(if (recording) color else color.copy(alpha = 0.85f), RoundedCornerShape(26.dp))
                    .semantics { role = Role.Button }
                    .pointerInput(granted) {
                        detectTapGestures(
                            onPress = {
                                if (!granted) {
                                    permission.launch(Manifest.permission.RECORD_AUDIO)
                                    return@detectTapGestures
                                }
                                recording = recorder.start(file)
                                if (!recording) return@detectTapGestures
                                tryAwaitRelease()
                                recording = false
                                if (recorder.stop()) currentOnRecorded()
                            },
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    when {
                        recording -> "Recording… let go to stop"
                        hasRecording -> "Hold to record again"
                        else -> "Hold to record"
                    },
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            }
            OutlinedButton(onClick = { recorder.play(file) }, enabled = hasRecording && !recording) { Text("Play") }
        }
    }
}

/** Wraps MediaRecorder's fussy lifecycle: start, stop (which throws on very short takes), release. */
private class YellRecording(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var target: File? = null
    private var startedAt = 0L

    /** Takes go here first, so a failed take never clobbers a good one. */
    private fun pending(file: File) = File(file.path + ".part")
    private var player: MediaPlayer? = null

    fun start(file: File): Boolean {
        release()
        return try {
            @Suppress("DEPRECATION")
            val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else MediaRecorder()
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioSamplingRate(44100)
            r.setAudioEncodingBitRate(96_000)
            r.setMaxDuration(MaxYellMs)
            r.setOutputFile(pending(file).path)
            r.prepare()
            r.start()
            recorder = r
            target = file
            startedAt = System.currentTimeMillis()
            true
        } catch (e: Exception) {
            Log.w("YellRecorder", "Could not start recording", e)
            recorder?.release()
            recorder = null
            false
        }
    }

    /** Stops and says whether the take was kept. A too-short take is thrown away. */
    fun stop(): Boolean {
        val r = recorder ?: return false
        val file = target ?: return false
        recorder = null
        target = null
        val longEnough = System.currentTimeMillis() - startedAt >= MinYellMs
        val ok = try {
            r.stop()
            true
        } catch (e: RuntimeException) {
            // MediaRecorder throws when stopped before it captured any audio.
            false
        } finally {
            r.release()
        }
        val part = pending(file)
        return if (ok && longEnough && part.renameTo(file)) {
            true
        } else {
            part.delete()
            false
        }
    }

    fun play(file: File) {
        player?.release()
        player = try {
            MediaPlayer().apply {
                setDataSource(file.path)
                prepare()
                start()
            }
        } catch (e: Exception) {
            Log.w("YellRecorder", "Could not play $file", e)
            null
        }
    }

    fun release() {
        recorder?.release()
        recorder = null
        player?.release()
        player = null
    }
}
