package com.exemple.cadi

import android.app.AlertDialog
import android.graphics.Paint
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class ListeActivity : AppCompatActivity() {

    private lateinit var adapter: ListeAdapter
    private lateinit var entete: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_liste)
        ListeCourses.init(this)
        MemoirePrix.init(this)

        entete = findViewById(R.id.listeEntete)
        adapter = ListeAdapter()
        findViewById<ListView>(R.id.listeItems).adapter = adapter

        findViewById<Button>(R.id.btnAjoutItem).setOnClickListener { ajouter() }
        findViewById<Button>(R.id.btnSuggestions).setOnClickListener { suggestions() }
        findViewById<Button>(R.id.btnNettoyer).setOnClickListener { nettoyer() }
        findViewById<Button>(R.id.btnFermerListe).setOnClickListener { finish() }

        maj()
    }

    override fun onResume() {
        super.onResume()
        maj()
    }

    private fun ajouter() {
        val input = EditText(this).apply {
            hint = "Ex : lait demi-écrémé"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        }
        AlertDialog.Builder(this)
            .setTitle("Ajouter à la liste")
            .setView(input)
            .setPositiveButton("Ajouter") { _, _ ->
                ListeCourses.ajouter(input.text.toString())
                maj()
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    /** Propose les produits que l'utilisateur achete le plus souvent. */
    private fun suggestions() {
        val freq = MemoirePrix.frequents(20)
            .filter { (nom, _) -> ListeCourses.items.none { it.nom.equals(nom, true) } }

        if (freq.isEmpty()) {
            Toast.makeText(
                this,
                "Pas encore assez d'historique — validez quelques courses d'abord",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        val noms = freq.map { it.first }.toTypedArray()
        val choisis = BooleanArray(noms.size)

        AlertDialog.Builder(this)
            .setTitle("Vos achats fréquents")
            .setMultiChoiceItems(noms, choisis) { _, i, ok -> choisis[i] = ok }
            .setPositiveButton("Ajouter") { _, _ ->
                freq.forEachIndexed { i, (nom, code) ->
                    if (choisis[i]) ListeCourses.ajouter(nom, code)
                }
                maj()
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    private fun nettoyer() {
        if (ListeCourses.items.isEmpty()) return
        AlertDialog.Builder(this)
            .setTitle("Nettoyer la liste")
            .setItems(
                arrayOf("Retirer les articles cochés", "Tout décocher", "Vider la liste")
            ) { _, i ->
                when (i) {
                    0 -> ListeCourses.viderCoches()
                    1 -> ListeCourses.toutDecocher()
                    2 -> ListeCourses.vider()
                }
                maj()
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    private fun maj() {
        val total = ListeCourses.items.size
        entete.text = when {
            total == 0 -> "Liste vide — ajoutez ce qu'il vous faut"
            ListeCourses.termine -> "✅ Liste complète ! $total article(s)"
            else -> "${ListeCourses.restants} restant(s) sur $total"
        }
        adapter.notifyDataSetChanged()
    }

    inner class ListeAdapter : BaseAdapter() {
        override fun getCount() = ListeCourses.items.size
        override fun getItem(i: Int) = ListeCourses.items[i]
        override fun getItemId(i: Int) = i.toLong()

        override fun getView(i: Int, convertView: View?, parent: ViewGroup): View {
            val v = convertView ?: layoutInflater.inflate(R.layout.item_liste, parent, false)
            val item = ListeCourses.items[i]

            val nom = v.findViewById<TextView>(R.id.itemNom)
            val case = v.findViewById<CheckBox>(R.id.itemCoche)

            nom.text = if (item.quantite > 1) "${item.nom}  ×${item.quantite}" else item.nom
            nom.paintFlags =
                if (item.coche) nom.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                else nom.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
            nom.alpha = if (item.coche) 0.45f else 1f

            case.setOnCheckedChangeListener(null)
            case.isChecked = item.coche
            case.setOnCheckedChangeListener { _, _ -> ListeCourses.basculer(i); maj() }

            v.findViewById<Button>(R.id.itemSuppr).setOnClickListener {
                ListeCourses.supprimer(i); maj()
            }
            return v
        }
    }
}
