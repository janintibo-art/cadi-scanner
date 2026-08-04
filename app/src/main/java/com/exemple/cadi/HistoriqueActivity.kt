package com.exemple.cadi

import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.util.Locale

class HistoriqueActivity : AppCompatActivity() {

    private lateinit var adapter: CourseAdapter
    private lateinit var graph: GraphMois
    private lateinit var resume: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_historique)
        Historique.init(this)

        graph = findViewById(R.id.graph)
        resume = findViewById(R.id.resume)
        adapter = CourseAdapter()
        findViewById<ListView>(R.id.listeCourses).adapter = adapter

        findViewById<Button>(R.id.btnExportHist).setOnClickListener {
            if (Historique.courses.isEmpty()) {
                Toast.makeText(this, "Aucune course archivée", Toast.LENGTH_SHORT).show()
            } else {
                Export.partagerCsv(
                    this,
                    Export.csvHistorique(Historique.courses),
                    "historique-courses.csv"
                )
            }
        }
        findViewById<Button>(R.id.btnFermerHist).setOnClickListener { finish() }

        maj()
    }

    private fun maj() {
        graph.afficher(Historique.parMois(6))
        resume.text = if (Historique.courses.isEmpty()) {
            "Aucune course archivée pour l'instant.\n" +
                "Utilisez « Valider les courses » à la fin de vos achats."
        } else {
            "Ce mois-ci : ${fmt(Historique.totalMoisCourant)}\n" +
                "Panier moyen : ${fmt(Historique.panierMoyen)} sur ${Historique.courses.size} course(s)"
        }
        adapter.notifyDataSetChanged()
    }

    private fun fmt(v: Double) = String.format(Locale.FRANCE, "%.2f €", v)

    inner class CourseAdapter : BaseAdapter() {
        override fun getCount() = Historique.courses.size
        override fun getItem(i: Int) = Historique.courses[i]
        override fun getItemId(i: Int) = i.toLong()

        override fun getView(i: Int, convertView: View?, parent: ViewGroup): View {
            val v = convertView ?: layoutInflater.inflate(R.layout.item_course, parent, false)
            val c = Historique.courses[i]

            v.findViewById<TextView>(R.id.courseEntete).text = "${c.dateCourte} — ${c.magasin}"
            v.findViewById<TextView>(R.id.courseDetail).text =
                "${fmt(c.total)} • ${c.nbArticles} article(s)"

            v.setOnClickListener { detail(c) }
            v.setOnLongClickListener { confirmerSuppression(c); true }
            return v
        }
    }

    private fun detail(c: Course) {
        val lignes = c.lignes.map { a ->
            "${a.nom}\n   ${fmt(a.prix)} × ${a.quantite} = ${fmt(a.total)}" +
                (a.promo?.let { "  ($it)" } ?: "")
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("${c.dateCourte} — ${fmt(c.total)}")
            .setItems(lignes, null)
            .setPositiveButton("Fermer", null)
            .setNeutralButton("Partager") { _, _ ->
                Export.partagerTexte(
                    this,
                    Export.texte(c.lignes, c.total, c.lignes.sumOf { it.economie }),
                    "Courses du ${c.dateCourte}"
                )
            }
            .show()
    }

    private fun confirmerSuppression(c: Course) {
        AlertDialog.Builder(this)
            .setTitle("Supprimer cette course ?")
            .setMessage("${c.dateCourte} — ${fmt(c.total)}")
            .setPositiveButton("Supprimer") { _, _ ->
                Historique.supprimer(c); maj()
            }
            .setNegativeButton("Annuler", null)
            .show()
    }
}
