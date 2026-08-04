package com.exemple.cadi

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/** Bip + vibration lors d'un scan, pour ne pas avoir a regarder l'ecran. */
class Feedback(ctx: Context) {

    private val vibrator: Vibrator? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            ctx.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    } catch (e: Exception) {
        null
    }

    private val tones: ToneGenerator? = try {
        ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
    } catch (e: Exception) {
        null
    }

    /** Article reconnu et ajoute : bip court aigu. */
    fun succes() {
        vibrer(60)
        tones?.startTone(ToneGenerator.TONE_PROP_BEEP, 120)
    }

    /** Rien trouve : double vibration, bip grave. */
    fun echec() {
        vibrer(longArrayOf(0, 40, 80, 40))
        tones?.startTone(ToneGenerator.TONE_SUP_ERROR, 200)
    }

    /** Budget depasse : vibration longue insistante. */
    fun alerte() {
        vibrer(longArrayOf(0, 150, 100, 150, 100, 150))
        tones?.startTone(ToneGenerator.TONE_CDMA_ABBR_ALERT, 400)
    }

    private fun vibrer(ms: Long) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            vibrator?.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
        else @Suppress("DEPRECATION") vibrator?.vibrate(ms)
    }

    private fun vibrer(motif: LongArray) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            vibrator?.vibrate(VibrationEffect.createWaveform(motif, -1))
        else @Suppress("DEPRECATION") vibrator?.vibrate(motif, -1)
    }

    fun liberer() = tones?.release()
}
