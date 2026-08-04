package com.exemple.cadi

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class Article(
    var prix: Double,
    var quantite: Int,
    var nom: String = "Article",
    var code: String? = null
)

/** Une action annulable. */
private class Action(val libelle: String, val annuler: () -> Unit)

/**
 * Etat partage entre l'ecran principal et l'ecran de scan.
 * Persiste automatiquement dans les SharedPreferences.
 */
object Caddie {

    val articles = mutableListOf<Article>()
    private var derniere: Action? = null
    private var prefs: android.content.SharedPreferences? = null

    /** Budget maximum en euros, 0 = desactive. */
    var budget: Double = 0.0
        set(v) { field = v; sauver() }

    val total: Double get() = articles.sumOf { it.prix * it.quantite }
    val nbArticles: Int get() = articles.sumOf { it.quantite }

    /** Part du budget consommee, ou null si aucun budget defini. */
    val ratioBudget: Double? get() = if (budget > 0) total / budget else null

    fun init(ctx: Context) {
        if (prefs != null) return
        prefs = ctx.applicationContext.getSharedPreferences("cadi", Context.MODE_PRIVATE)
        charger()
    }

    // ---------- Operations ----------

    fun ajouter(prix: Double, quantite: Int, nom: String, code: String?) {
        val existant = articles.find { it.code != null && it.code == code }
            ?: articles.find { it.prix == prix && it.nom == nom }

        if (existant != null) {
            val avant = existant.quantite
            existant.quantite += quantite
            memoriser("$quantite × $nom") { existant.quantite = avant }
        } else {
            val a = Article(prix, quantite, nom, code)
            articles.add(0, a)
            memoriser("$quantite × $nom") { articles.remove(a) }
        }
        sauver()
    }

    fun changerQuantite(index: Int, delta: Int) {
        val a = articles.getOrNull(index) ?: return
        if (a.quantite + delta <= 0) {
            articles.removeAt(index)
            memoriser("Retrait de ${a.nom}") { articles.add(index, a) }
        } else {
            a.quantite += delta
            memoriser("Quantité de ${a.nom}") { a.quantite -= delta }
        }
        sauver()
    }

    fun supprimer(index: Int) {
        val a = articles.getOrNull(index) ?: return
        articles.removeAt(index)
        memoriser("Retrait de ${a.nom}") { articles.add(index, a) }
        sauver()
    }

    fun vider() {
        val copie = articles.toList()
        articles.clear()
        memoriser("Caddie vidé") { articles.addAll(copie) }
        sauver()
    }

    // ---------- Annulation ----------

    private fun memoriser(libelle: String, annuler: () -> Unit) {
        derniere = Action(libelle, annuler)
    }

    fun libelleAnnulation(): String? = derniere?.libelle

    /** Annule la derniere action. Renvoie true si quelque chose a ete annule. */
    fun annuler(): Boolean {
        val a = derniere ?: return false
        a.annuler()
        derniere = null
        sauver()
        return true
    }

    // ---------- Persistance ----------

    private fun sauver() {
        val arr = JSONArray()
        articles.forEach {
            arr.put(
                JSONObject().put("p", it.prix).put("q", it.quantite)
                    .put("n", it.nom).put("c", it.code ?: "")
            )
        }
        prefs?.edit()
            ?.putString("articles", arr.toString())
            ?.putFloat("budget", budget.toFloat())
            ?.apply()
    }

    private fun charger() {
        val p = prefs ?: return
        budget = p.getFloat("budget", 0f).toDouble()
        try {
            val arr = JSONArray(p.getString("articles", "[]"))
            articles.clear()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                articles.add(
                    Article(
                        o.getDouble("p"), o.getInt("q"),
                        o.optString("n", "Article"),
                        o.optString("c").takeIf { it.isNotBlank() }
                    )
                )
            }
        } catch (_: Exception) { }
    }
}
