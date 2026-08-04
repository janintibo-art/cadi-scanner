package com.exemple.cadi

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Un article a acheter, coche quand il est mis dans le caddie. */
data class ItemListe(
    var nom: String,
    var code: String? = null,
    var quantite: Int = 1,
    var coche: Boolean = false
)

/**
 * Liste de courses preparee avant d'aller au magasin.
 * Les articles se cochent automatiquement quand on les scanne.
 */
object ListeCourses {

    val items = mutableListOf<ItemListe>()
    private var prefs: android.content.SharedPreferences? = null

    fun init(ctx: Context) {
        if (prefs != null) return
        prefs = ctx.applicationContext.getSharedPreferences("cadi_liste", Context.MODE_PRIVATE)
        charger()
    }

    val restants: Int get() = items.count { !it.coche }
    val termine: Boolean get() = items.isNotEmpty() && restants == 0

    fun ajouter(nom: String, code: String? = null, quantite: Int = 1) {
        if (nom.isBlank()) return
        val existant = items.find {
            (code != null && it.code == code) || it.nom.equals(nom, ignoreCase = true)
        }
        if (existant != null) existant.quantite += quantite
        else items.add(ItemListe(nom.trim(), code, quantite))
        sauver()
    }

    fun basculer(index: Int) {
        items.getOrNull(index)?.let { it.coche = !it.coche }
        sauver()
    }

    fun supprimer(index: Int) {
        if (index in items.indices) items.removeAt(index)
        sauver()
    }

    fun viderCoches() {
        items.removeAll { it.coche }
        sauver()
    }

    fun toutDecocher() {
        items.forEach { it.coche = false }
        sauver()
    }

    fun vider() {
        items.clear()
        sauver()
    }

    /**
     * Coche l'article correspondant a un code scanne.
     * Renvoie le nom de l'article coche, ou null si absent de la liste.
     */
    fun cocherParCode(code: String): String? {
        val item = items.find { it.code == code && !it.coche } ?: return null
        item.coche = true
        sauver()
        return item.nom
    }

    /** Meme chose par nom approchant, pour les articles saisis sans code. */
    fun cocherParNom(nom: String): String? {
        val cible = nom.lowercase().trim()
        val item = items.find {
            !it.coche && (it.nom.lowercase().contains(cible) || cible.contains(it.nom.lowercase()))
        } ?: return null
        item.coche = true
        sauver()
        return item.nom
    }

    /** Associe un code-barres a un article saisi sans code, pour les fois suivantes. */
    fun associerCode(nom: String, code: String) {
        items.find { it.nom.equals(nom, ignoreCase = true) && it.code == null }?.let {
            it.code = code
            sauver()
        }
    }

    // ---------- Persistance ----------

    private fun sauver() {
        val arr = JSONArray()
        items.forEach {
            arr.put(
                JSONObject().put("n", it.nom).put("c", it.code ?: "")
                    .put("q", it.quantite).put("k", it.coche)
            )
        }
        prefs?.edit()?.putString("items", arr.toString())?.apply()
    }

    private fun charger() {
        val p = prefs ?: return
        try {
            val arr = JSONArray(p.getString("items", "[]"))
            items.clear()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                items.add(
                    ItemListe(
                        o.optString("n"), o.optString("c").takeIf { it.isNotBlank() },
                        o.optInt("q", 1), o.optBoolean("k", false)
                    )
                )
            }
        } catch (_: Exception) { }
    }
}
