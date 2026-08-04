package com.exemple.cadi

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Un prix que l'utilisateur a reellement paye. */
data class MonPrix(
    val code: String?,
    val nom: String,
    val prix: Double,
    val magasin: String,
    val horodatage: Long
) {
    val dateCourte: String
        get() = SimpleDateFormat("dd/MM/yy", Locale.FRANCE).format(Date(horodatage))

    val ageJours: Long
        get() = (System.currentTimeMillis() - horodatage) / 86_400_000L
}

/** Synthese de ce que l'utilisateur a paye pour un produit donne. */
data class Synthese(
    val releves: List<MonPrix>
) {
    val dernier: MonPrix? get() = releves.maxByOrNull { it.horodatage }
    val moinsCher: MonPrix? get() = releves.minByOrNull { it.prix }
    val plusCher: MonPrix? get() = releves.maxByOrNull { it.prix }
    val moyenne: Double get() = if (releves.isEmpty()) 0.0 else releves.map { it.prix }.average()

    /** Texte pret a afficher, ou null si on n'a jamais achete ce produit. */
    fun resume(): String? {
        val d = dernier ?: return null
        val mc = moinsCher!!
        return buildString {
            append("Vous l'avez payé ${euro(d.prix)} chez ${d.magasin} le ${d.dateCourte}")
            if (releves.size > 1) {
                append("\nVos ${releves.size} achats : de ${euro(mc.prix)} à ${euro(plusCher!!.prix)}")
                append(" (moyenne ${euro(moyenne)})")
                if (mc.magasin != d.magasin)
                    append("\n💡 Le moins cher était chez ${mc.magasin}")
            }
        }
    }

    /** Alerte si le prix propose est nettement au-dessus de l'habitude. */
    fun alerteHausse(prixActuel: Double): String? {
        if (releves.size < 2) return null
        val seuil = moyenne * 1.10
        return if (prixActuel > seuil)
            "⚠️ ${euro(prixActuel)} c'est ${String.format(Locale.FRANCE, "%.0f", (prixActuel / moyenne - 1) * 100)} % " +
                "au-dessus de votre moyenne (${euro(moyenne)})"
        else null
    }

    private fun euro(v: Double) = String.format(Locale.FRANCE, "%.2f €", v)
}

/**
 * Historique personnel des prix payes. Au bout de quelques mois c'est
 * plus fiable qu'une base collaborative pour vos magasins habituels.
 */
object MemoirePrix {

    private val releves = mutableListOf<MonPrix>()
    private var prefs: android.content.SharedPreferences? = null

    /** On garde au maximum ce nombre de releves pour ne pas gonfler indefiniment. */
    private const val MAX = 2000

    fun init(ctx: Context) {
        if (prefs != null) return
        prefs = ctx.applicationContext.getSharedPreferences("cadi_memoire", Context.MODE_PRIVATE)
        charger()
    }

    /** Enregistre tous les articles d'une course validee. */
    fun enregistrerCourse(articles: List<Article>, magasin: String) {
        val t = System.currentTimeMillis()
        articles.forEach {
            releves.add(MonPrix(it.code, it.nom, it.prix, magasin.ifBlank { "Magasin" }, t))
        }
        if (releves.size > MAX) {
            releves.sortByDescending { it.horodatage }
            while (releves.size > MAX) releves.removeAt(releves.lastIndex)
        }
        sauver()
    }

    /** Recherche par code-barres en priorite, sinon par nom. */
    fun synthese(code: String?, nom: String? = null): Synthese {
        val trouves = when {
            !code.isNullOrBlank() -> releves.filter { it.code == code }
            !nom.isNullOrBlank() -> releves.filter { it.nom.equals(nom, ignoreCase = true) }
            else -> emptyList()
        }
        return Synthese(trouves)
    }

    /** Produits achetes le plus souvent, pour alimenter la liste de courses. */
    fun frequents(limite: Int = 20): List<Pair<String, String?>> =
        releves.groupBy { it.nom }
            .entries
            .sortedByDescending { it.value.size }
            .take(limite)
            .map { it.key to it.value.firstOrNull()?.code }

    val nbReleves: Int get() = releves.size

    fun vider() {
        releves.clear()
        sauver()
    }

    // ---------- Persistance ----------

    private fun sauver() {
        val arr = JSONArray()
        releves.forEach {
            arr.put(
                JSONObject().put("c", it.code ?: "").put("n", it.nom)
                    .put("p", it.prix).put("m", it.magasin).put("t", it.horodatage)
            )
        }
        prefs?.edit()?.putString("releves", arr.toString())?.apply()
    }

    private fun charger() {
        val p = prefs ?: return
        try {
            val arr = JSONArray(p.getString("releves", "[]"))
            releves.clear()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                releves.add(
                    MonPrix(
                        o.optString("c").takeIf { it.isNotBlank() },
                        o.optString("n", "Article"),
                        o.getDouble("p"),
                        o.optString("m", "Magasin"),
                        o.getLong("t")
                    )
                )
            }
        } catch (_: Exception) { }
    }
}
