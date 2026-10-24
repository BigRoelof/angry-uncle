package nl.angryuncle.game

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import android.util.Log
import nl.angryuncle.R
import java.io.File
import kotlin.random.Random

/**
 * All game sounds. Short effects go through a SoundPool (instant, pitch-shiftable);
 * a head's own recorded yell is a file and plays through a MediaPlayer.
 */
class Sounds(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_GAME)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()
    private val pool = SoundPool.Builder().setMaxStreams(8).setAudioAttributes(attributes).build()

    private val pops = listOf(R.raw.sfx_pop_1, R.raw.sfx_pop_2, R.raw.sfx_pop_3).map { pool.load(context, it, 1) }
    private val boings = listOf(R.raw.sfx_boing_low, R.raw.sfx_boing_high).map { pool.load(context, it, 1) }
    private val sting = pool.load(context, R.raw.sfx_sting, 1)
    private val punch = pool.load(context, R.raw.sfx_punch, 1)
    private val rumble = pool.load(context, R.raw.sfx_rumble, 1)
    private val growl = pool.load(context, R.raw.sfx_growl, 1)

    private var yellPlayer: MediaPlayer? = null

    var enabled: Boolean = prefs.getBoolean("sound", true)
        set(value) {
            field = value
            prefs.edit().putBoolean("sound", value).apply()
            if (!value) stopYell()
        }

    private fun play(id: Int, volume: Float = 1f, rate: Float = 1f) {
        if (enabled) pool.play(id, volume, volume, 1, 0, rate)
    }

    /** A pop with a boing on top, both at a random pitch so a whole round never sounds the same twice. */
    fun safeTap() {
        play(pops.random(), 0.9f, Random.nextFloat() * 0.3f + 0.9f)
        play(boings.random(), 0.45f, Random.nextFloat() * 0.4f + 0.85f)
    }

    /** The moment he is found: a dramatic hit, a punch and the room shaking. */
    fun angryReveal() {
        play(sting)
        play(punch, 0.8f, 0.8f)
        play(rumble, 0.9f)
    }

    /** The angry yell: the head's own recording when it has one, otherwise the default growl. */
    fun yell(recording: File?) {
        if (!enabled) return
        if (recording == null || !recording.exists()) {
            play(growl, 1f, Random.nextFloat() * 0.2f + 0.9f)
            return
        }
        stopYell()
        yellPlayer = try {
            MediaPlayer().apply {
                setAudioAttributes(attributes)
                setDataSource(recording.path)
                setOnCompletionListener { stopYell() }
                prepare()
                start()
            }
        } catch (e: Exception) {
            Log.w("Sounds", "Could not play $recording", e)
            play(growl)
            null
        }
    }

    private fun stopYell() {
        yellPlayer?.release()
        yellPlayer = null
    }

    fun release() {
        stopYell()
        pool.release()
    }
}
