package com.exemple.cadi

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import android.view.animation.OvershootInterpolator
import java.util.Locale
import kotlin.math.abs

/**
 * Le total affiche comme une etiquette de gondole : chiffres condenses
 * en tres grand, centimes sureleves, bord inferieur dentele comme un
 * ticket detachable. C'est l'element central de l'ecran.
 */
class EtiquetteTotal @JvmOverloads constructor(
    ctx: Context, attrs: AttributeSet? = null
) : View(ctx, attrs) {

    private var montant = 0.0
    private var montantAffiche = 0.0
    private var budget = 0.0
    private var sousTitre = ""
    private var animateur: ValueAnimator? = null

    private val condense = Typeface.create("sans-serif-condensed", Typeface.BOLD)
    private val etroit = Typeface.create("sans-serif-condensed", Typeface.NORMAL)

    private val fond = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ombre = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x33000000 }
    private val euros = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = condense; color = 0xFFFFFFFF.toInt(); textAlign = Paint.Align.RIGHT
    }
    private val centimes = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = condense; color = 0xFFFFFFFF.toInt(); textAlign = Paint.Align.LEFT
    }
    private val symbole = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = etroit; color = 0xB3FFFFFF.toInt(); textAlign = Paint.Align.LEFT
    }
    private val legende = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = etroit; color = 0xCCFFFFFF.toInt(); textAlign = Paint.Align.CENTER
    }
    private val jauge = Paint(Paint.ANTI_ALIAS_FLAG)
    private val jaugeFond = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x33000000 }

    private val chemin = Path()
    private val rect = RectF()

    /** Met a jour le montant avec un decompte anime. */
    fun afficher(nouveauTotal: Double, nouveauBudget: Double, legendeTexte: String) {
        budget = nouveauBudget
        sousTitre = legendeTexte

        if (abs(nouveauTotal - montant) < 0.005) {
            montantAffiche = nouveauTotal; montant = nouveauTotal; invalidate(); return
        }

        val depart = montantAffiche
        montant = nouveauTotal
        animateur?.cancel()
        animateur = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 420
            interpolator = OvershootInterpolator(0.8f)
            addUpdateListener {
                val f = it.animatedValue as Float
                montantAffiche = depart + (nouveauTotal - depart) * f
                invalidate()
            }
            start()
        }
        // Petit sursaut de l'etiquette a chaque ajout
        animate().scaleX(1.03f).scaleY(1.03f).setDuration(120).withEndAction {
            animate().scaleX(1f).scaleY(1f).setDuration(180).start()
        }.start()
    }

    /** Couleur de fond selon la consommation du budget. */
    private fun couleurs(): Pair<Int, Int> {
        val r = if (budget > 0) montant / budget else 0.0
        return when {
            budget <= 0 -> 0xFF178A60.toInt() to 0xFF0A4A33.toInt()
            r >= 1.0 -> 0xFFD8383E.toInt() to 0xFF8E1B20.toInt()
            r >= 0.8 -> 0xFFF08C1A.toInt() to 0xFFB35A00.toInt()
            else -> 0xFF178A60.toInt() to 0xFF0A4A33.toInt()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val l = width.toFloat()
        val h = height.toFloat()
        if (l <= 0 || h <= 0) return

        val dents = 12f          // hauteur du bord dentele
        val corps = h - dents

        val (clair, sombre) = couleurs()

        // Ombre portee : le meme trace decale vers le bas
        canvas.save()
        canvas.translate(0f, 5f)
        tracerEtiquette(l, corps, dents)
        canvas.drawPath(chemin, ombre)
        canvas.restore()

        // Corps de l'etiquette, en degrade vertical pour le relief
        fond.shader = LinearGradient(0f, 0f, 0f, corps, clair, sombre, Shader.TileMode.CLAMP)
        tracerEtiquette(l, corps, dents)
        canvas.drawPath(chemin, fond)

        // Reflet en haut, comme un plastique d'etiquette
        val reflet = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, 0f, corps * 0.45f,
                0x26FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP
            )
        }
        canvas.drawPath(chemin, reflet)

        // ----- Le montant -----
        val entier = montantAffiche.toInt()
        val cents = ((abs(montantAffiche) - abs(entier.toDouble())) * 100).toInt()

        val tailleEuros = corps * 0.52f
        euros.textSize = tailleEuros
        centimes.textSize = tailleEuros * 0.46f
        symbole.textSize = tailleEuros * 0.34f

        val texteEuros = entier.toString()
        val texteCents = String.format(Locale.FRANCE, "%02d", cents)

        val largeurCents = centimes.measureText(texteCents)
        val largeurSymbole = symbole.measureText("€")
        val largeurEuros = euros.measureText(texteEuros)
        val total = largeurEuros + 4f + largeurCents + 3f + largeurSymbole

        val gauche = (l - total) / 2f
        val baseEuros = corps * 0.62f

        canvas.drawText(texteEuros, gauche + largeurEuros, baseEuros, euros)
        // virgule discrete
        canvas.drawText(",", gauche + largeurEuros + 1f, baseEuros, symbole)
        // centimes sureleves, comme sur une vraie etiquette de prix
        canvas.drawText(
            texteCents,
            gauche + largeurEuros + 4f,
            baseEuros - tailleEuros * 0.42f,
            centimes
        )
        canvas.drawText(
            "€",
            gauche + largeurEuros + 4f + largeurCents + 3f,
            baseEuros - tailleEuros * 0.42f,
            symbole
        )

        // ----- Legende -----
        legende.textSize = corps * 0.13f
        canvas.drawText(sousTitre, l / 2f, corps * 0.82f, legende)

        // ----- Jauge de budget -----
        if (budget > 0) {
            val part = (montant / budget).coerceIn(0.0, 1.0).toFloat()
            val y = corps * 0.885f
            val hj = corps * 0.045f
            val marge = l * 0.08f

            rect.set(marge, y, l - marge, y + hj)
            canvas.drawRoundRect(rect, hj / 2, hj / 2, jaugeFond)

            jauge.color = 0xE6FFFFFF.toInt()
            rect.set(marge, y, marge + (l - 2 * marge) * part, y + hj)
            canvas.drawRoundRect(rect, hj / 2, hj / 2, jauge)
        }
    }

    /** Rectangle arrondi en haut, dentele en bas comme un ticket detachable. */
    private fun tracerEtiquette(l: Float, corps: Float, dents: Float) {
        chemin.reset()
        val r = 18f
        chemin.moveTo(0f, r)
        chemin.quadTo(0f, 0f, r, 0f)
        chemin.lineTo(l - r, 0f)
        chemin.quadTo(l, 0f, l, r)
        chemin.lineTo(l, corps)

        // dents triangulaires
        val nb = 22
        val pas = l / nb
        for (i in 0 until nb) {
            val x = l - i * pas
            chemin.lineTo(x - pas / 2, corps + dents)
            chemin.lineTo(x - pas, corps)
        }
        chemin.lineTo(0f, corps)
        chemin.close()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animateur?.cancel()
    }
}
