package nl.angryuncle.game

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/** Vibration for taps: a light tick for a safe uncle, a long angry rumble for the angry one. */
class Haptics(context: Context) {
    private val vibrator: Vibrator? =
        if (Build.VERSION.SDK_INT >= 31) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }

    fun safeTap() {
        val v = vibrator ?: return
        if (Build.VERSION.SDK_INT >= 29) {
            v.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
        } else {
            v.vibrate(VibrationEffect.createOneShot(15, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }

    fun angry() {
        val v = vibrator ?: return
        // Off/on pairs in ms: a hard hit, then a grumbling rumble.
        val timings = longArrayOf(0, 220, 60, 90, 50, 90, 50, 300)
        val amplitudes = intArrayOf(0, 255, 0, 200, 0, 200, 0, 140)
        v.vibrate(
            if (v.hasAmplitudeControl()) VibrationEffect.createWaveform(timings, amplitudes, -1)
            else VibrationEffect.createWaveform(timings, -1),
        )
    }
}
