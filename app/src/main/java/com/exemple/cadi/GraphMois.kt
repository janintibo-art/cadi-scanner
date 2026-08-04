package com.exemple.cadi

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import java.util.Locale

/** Petit graphique en barres pour l'evolution mensuelle des depenses. */
class GraphMois @JvmOverloads constructor(
    ctx: Context, attrs: AttributeSet? = null
) : View(ctx, attrs) {

    private var donnees: List<Pair<String, Double>> = emptyList()

    private val barre = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF2E7D32.toInt() }
    private val texte = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.DKGRAY
        textSize = 26f
        textAlign = Paint.Align.CENTER
    }
    private val montant = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = 26f
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    fun afficher(valeurs: List<Pair<String, Double>>) {
        donnees = valeurs
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (donnees.isEmpty()) return

        val max = donnees.maxOf { it.second }.coerceAtLeast(1.0)
        val margeBas = 34f
        val margeHaut = 34f
        val largeurCase = width / donnees.size.toFloat()
        val largeurBarre = largeurCase * 0.55f
        val hauteurDispo = height - margeBas - margeHaut

        donnees.forEachIndexed { i, (nom, valeur) ->
            val centre = largeurCase * i + largeurCase / 2
            val h = (valeur / max * hauteurDispo).toFloat()
            val bas = height - margeBas

            barre.color = if (valeur == max && max > 0) 0xFF1B5E20.toInt() else 0xFF66BB6A.toInt()
            canvas.drawRoundRect(
                centre - largeurBarre / 2, bas - h,
                centre + largeurBarre / 2, bas,
                8f, 8f, barre
            )

            canvas.drawText(nom, centre, height - 8f, texte)
            if (valeur > 0) {
                canvas.drawText(
                    String.format(Locale.FRANCE, "%.0f€", valeur),
                    centre, bas - h - 8f, montant
                )
            }
        }
    }
}
