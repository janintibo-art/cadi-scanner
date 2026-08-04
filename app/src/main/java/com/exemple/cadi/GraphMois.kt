package com.exemple.cadi

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.animation.ValueAnimator
import java.util.Locale

/** Depenses mensuelles, barres en degrade avec apparition animee. */
class GraphMois @JvmOverloads constructor(
    ctx: Context, attrs: AttributeSet? = null
) : View(ctx, attrs) {

    private var donnees: List<Pair<String, Double>> = emptyList()
    private var progression = 0f

    private val condense = Typeface.create("sans-serif-condensed", Typeface.BOLD)

    private val barre = Paint(Paint.ANTI_ALIAS_FLAG)
    private val socle = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x14000000 }
    private val ligne = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x22000000; strokeWidth = 1.5f
    }
    private val mois = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF5A6169.toInt(); textSize = 26f
        textAlign = Paint.Align.CENTER; typeface = condense
    }
    private val valeur = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF14181C.toInt(); textSize = 27f
        textAlign = Paint.Align.CENTER; typeface = condense
    }
    private val rect = RectF()

    fun afficher(valeurs: List<Pair<String, Double>>) {
        donnees = valeurs
        progression = 0f
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 650
            interpolator = DecelerateInterpolator(1.6f)
            addUpdateListener { progression = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (donnees.isEmpty()) return

        val max = donnees.maxOf { it.second }.coerceAtLeast(1.0)
        val bas = height - 36f
        val haut = 38f
        val dispo = bas - haut
        val case_ = width / donnees.size.toFloat()
        val large = case_ * 0.46f

        canvas.drawLine(0f, bas, width.toFloat(), bas, ligne)

        donnees.forEachIndexed { i, (nom, v) ->
            val cx = case_ * i + case_ / 2
            val h = (v / max * dispo).toFloat() * progression

            // socle discret sous chaque barre
            rect.set(cx - large / 2, bas - 3f, cx + large / 2, bas)
            canvas.drawRoundRect(rect, 3f, 3f, socle)

            if (h > 2f) {
                val estMax = v >= max
                barre.shader = LinearGradient(
                    0f, bas - h, 0f, bas,
                    if (estMax) 0xFF178A60.toInt() else 0xFF7FC4A3.toInt(),
                    if (estMax) 0xFF0A4A33.toInt() else 0xFF4E9E7B.toInt(),
                    Shader.TileMode.CLAMP
                )
                rect.set(cx - large / 2, bas - h, cx + large / 2, bas)
                canvas.drawRoundRect(rect, 7f, 7f, barre)

                if (progression > 0.75f) {
                    valeur.alpha = ((progression - 0.75f) / 0.25f * 255).toInt().coerceIn(0, 255)
                    canvas.drawText(
                        String.format(Locale.FRANCE, "%.0f €", v),
                        cx, bas - h - 11f, valeur
                    )
                }
            }
            canvas.drawText(nom.uppercase(), cx, height - 10f, mois)
        }
    }
}
