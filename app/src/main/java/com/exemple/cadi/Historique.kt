package com.exemple.cadi

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Une session de courses archivee. */
data class Course(
    val horodatage: Long,
    val magasin: String,
    val total: Double,
    val nbArticles: Int,
    val lignes: List<Article>
) {
    val date: Date get() = Date(horodatage)

    val dateCourte: String
        get() = SimpleDateFormat("dd/MM/yyyy", Locale.FRANCE).format(date)

    /** Cle "2026-08" pour le regroupement mensuel. */
    val cleMois: String
        get() = SimpleDateFormat("yyyy-MM", Locale.FRANCE).format(date)

    val moisLisible: String
        get() = SimpleDateFormat("MMM yy", Locale.FRANCE).format(date)
}

object Historique {

    val courses = mutableListOf<Course>()
    private var prefs: android.content.SharedPreferences? = null

    fun init(ctx: Context) {
        if (prefs != null) return
        prefs = ctx.applicationContext.getSharedPreferences("cadi_hist", Context.MODE_PRIVATE)
        charger()
    }

    /** Archive le caddie courant et le vide. */
    fun archiver(magasin: String): Course {
        val c = Course(
            horodatage = System.currentTimeMillis(),
            magasin = magasin.ifBlank { "Magasin" },
            total = Caddie.total,
            nbArticles = Caddie.nbArticles,
            lignes = Caddie.articles.map { it.copy() }
        )
        courses.add(0, c)
        sauver()
        return c
    }

    fun supprimer(c: Course) {
        courses.remove(c)
        sauver()
    }

    // ---------- Statistiques ----------

    /** Totaux des N derniers mois, du plus ancien au plus recent. */
    fun parMois(nbMois: Int = 6): List<Pair<String, Double>> {
        val cal = Calendar.getInstance()
        val fmtCle = SimpleDateFormat("yyyy-MM", Locale.FRANCE)
        val fmtNom = SimpleDateFormat("MMM", Locale.FRANCE)

        val resultat = mutableListOf<Pair<String, Double>>()
        cal.add(Calendar.MONTH, -(nbMois - 1))
        repeat(nbMois) {
            val cle = fmtCle.format(cal.time)
            val nom = fmtNom.format(cal.time)
            val somme = courses.filter { it.cleMois == cle }.sumOf { it.total }
            resultat.add(nom to somme)
            cal.add(Calendar.MONTH, 1)
        }
        return resultat
    }

    val totalMoisCourant: Double
        get() {
            val cle = SimpleDateFormat("yyyy-MM", Locale.FRANCE).format(Date())
            return courses.filter { it.cleMois == cle }.sumOf { it.total }
        }

    val panierMoyen: Double
        get() = if (courses.isEmpty()) 0.0 else courses.sumOf { it.total } / courses.size

    // ---------- Persistance ----------

    private fun sauver() {
        val arr = JSONArray()
        courses.forEach { c ->
            val lignes = JSONArray()
            c.lignes.forEach {
                lignes.put(
                    JSONObject().put("p", it.prix).put("q", it.quantite)
                        .put("n", it.nom).put("c", it.code ?: "")
                        .put("pr", it.promo ?: "")
                )
            }
            arr.put(
                JSONObject()
                    .put("t", c.horodatage).put("m", c.magasin)
                    .put("s", c.total).put("nb", c.nbArticles)
                    .put("l", lignes)
            )
        }
        prefs?.edit()?.putString("courses", arr.toString())?.apply()
    }

    private fun charger() {
        val p = prefs ?: return
        try {
            val arr = JSONArray(p.getString("courses", "[]"))
            courses.clear()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val lignesJson = o.optJSONArray("l") ?: JSONArray()
                val lignes = (0 until lignesJson.length()).map { j ->
                    val a = lignesJson.getJSONObject(j)
                    Article(
                        a.getDouble("p"), a.getInt("q"),
                        a.optString("n", "Article"),
                        a.optString("c").takeIf { it.isNotBlank() },
                        a.optString("pr").takeIf { it.isNotBlank() }
                    )
                }
                courses.add(
                    Course(
                        o.getLong("t"), o.optString("m", "Magasin"),
                        o.getDouble("s"), o.optInt("nb"), lignes
                    )
                )
            }
        } catch (_: Exception) { }
    }
}
