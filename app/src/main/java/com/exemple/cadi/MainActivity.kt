package com.exemple.cadi

import android.app.AlertDialog
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale

data class Article(var prix: Double, var quantite: Int)

class MainActivity : AppCompatActivity() {

    private val articles = mutableListOf<Article>()
    private lateinit var adapter: ArticleAdapter
    private lateinit var totalView: TextView
    private var photoFile: File? = null

    private val prendrePhoto =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
            if (ok) photoFile?.let { analyserPhoto(it) }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        totalView = findViewById(R.id.total)
        adapter = ArticleAdapter()
        findViewById<ListView>(R.id.liste).adapter = adapter

        findViewById<Button>(R.id.btnPhoto).setOnClickListener { lancerCamera() }
        findViewById<Button>(R.id.btnManuel).setOnClickListener { saisieManuelle() }
        findViewById<Button>(R.id.btnVider).setOnClickListener { confirmerVider() }

        charger()
        maj()
    }

    // ---------- Camera ----------

    private fun lancerCamera() {
        val f = File(cacheDir, "etiquette.jpg")
        photoFile = f
        val uri: Uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", f)
        prendrePhoto.launch(uri)
    }

    // ---------- OCR ----------

    private fun analyserPhoto(fichier: File) {
        try {
            val image = InputImage.fromFilePath(this, Uri.fromFile(fichier))
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            Toast.makeText(this, "Analyse en cours…", Toast.LENGTH_SHORT).show()
            recognizer.process(image)
                .addOnSuccessListener { resultat -> traiterTexte(resultat.text) }
                .addOnFailureListener {
                    Toast.makeText(this, "Erreur d'analyse : ${it.message}", Toast.LENGTH_LONG).show()
                }
        } catch (e: Exception) {
            Toast.makeText(this, "Impossible de lire la photo", Toast.LENGTH_LONG).show()
        }
    }

    private fun traiterTexte(texte: String) {
        // Cherche les montants du type 1,99 / 12.50 / 3 , 45 etc.
        val regex = Regex("""(\d{1,4})\s*[.,]\s*(\d{2})(?!\d)""")
        val candidats = regex.findAll(texte)
            .map { it.groupValues[1].toInt() + it.groupValues[2].toInt() / 100.0 }
            .filter { it in 0.05..2000.0 }
            .distinct()
            .toList()

        when {
            candidats.isEmpty() -> {
                Toast.makeText(this, "Aucun prix détecté, réessayez ou saisissez-le", Toast.LENGTH_LONG).show()
                saisieManuelle()
            }
            candidats.size == 1 -> demanderQuantite(candidats[0])
            else -> {
                // Plusieurs nombres trouvés (prix, prix au kilo…) : on laisse choisir
                val libelles = candidats.map { fmt(it) }.toTypedArray()
                AlertDialog.Builder(this)
                    .setTitle("Quel est le prix de l'article ?")
                    .setItems(libelles) { _, i -> demanderQuantite(candidats[i]) }
                    .setNegativeButton("Annuler", null)
                    .show()
            }
        }
    }

    // ---------- Ajout / saisie ----------

    private fun demanderQuantite(prix: Double) {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText("1")
            setSelection(1)
        }
        AlertDialog.Builder(this)
            .setTitle("Article à ${fmt(prix)}")
            .setMessage("Quantité :")
            .setView(input)
            .setPositiveButton("Ajouter") { _, _ ->
                val q = input.text.toString().toIntOrNull() ?: 1
                ajouter(prix, if (q < 1) 1 else q)
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    private fun saisieManuelle() {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            hint = "Ex : 2,49"
        }
        AlertDialog.Builder(this)
            .setTitle("Prix de l'article")
            .setView(input)
            .setPositiveButton("OK") { _, _ ->
                val p = input.text.toString().replace(',', '.').toDoubleOrNull()
                if (p != null && p > 0) demanderQuantite(p)
                else Toast.makeText(this, "Prix invalide", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    private fun ajouter(prix: Double, quantite: Int) {
        // Si le meme prix existe deja, on augmente juste la quantite
        val existant = articles.find { it.prix == prix }
        if (existant != null) existant.quantite += quantite
        else articles.add(0, Article(prix, quantite))
        maj()
        Toast.makeText(this, "Ajouté : ${quantite} × ${fmt(prix)}", Toast.LENGTH_SHORT).show()
    }

    private fun confirmerVider() {
        if (articles.isEmpty()) return
        AlertDialog.Builder(this)
            .setTitle("Vider le caddie ?")
            .setPositiveButton("Oui") { _, _ -> articles.clear(); maj() }
            .setNegativeButton("Non", null)
            .show()
    }

    // ---------- Affichage / sauvegarde ----------

    private fun maj() {
        val total = articles.sumOf { it.prix * it.quantite }
        val nb = articles.sumOf { it.quantite }
        totalView.text = "Total : ${fmt(total)}  ($nb article${if (nb > 1) "s" else ""})"
        adapter.notifyDataSetChanged()
        sauver()
    }

    private fun fmt(v: Double) = String.format(Locale.FRANCE, "%.2f €", v)

    private fun sauver() {
        val arr = JSONArray()
        articles.forEach {
            arr.put(JSONObject().put("p", it.prix).put("q", it.quantite))
        }
        getSharedPreferences("cadi", MODE_PRIVATE).edit()
            .putString("articles", arr.toString()).apply()
    }

    private fun charger() {
        try {
            val s = getSharedPreferences("cadi", MODE_PRIVATE).getString("articles", null) ?: return
            val arr = JSONArray(s)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                articles.add(Article(o.getDouble("p"), o.getInt("q")))
            }
        } catch (_: Exception) { }
    }

    // ---------- Liste ----------

    inner class ArticleAdapter : BaseAdapter() {
        override fun getCount() = articles.size
        override fun getItem(i: Int) = articles[i]
        override fun getItemId(i: Int) = i.toLong()

        override fun getView(i: Int, convertView: View?, parent: ViewGroup): View {
            val v = convertView ?: layoutInflater.inflate(R.layout.item_article, parent, false)
            val a = articles[i]

            v.findViewById<TextView>(R.id.ligne).text =
                "${fmt(a.prix)} × ${a.quantite} = ${fmt(a.prix * a.quantite)}"
            v.findViewById<TextView>(R.id.qte).text = a.quantite.toString()

            v.findViewById<Button>(R.id.btnMoins).setOnClickListener {
                if (a.quantite > 1) a.quantite-- else articles.removeAt(i)
                maj()
            }
            v.findViewById<Button>(R.id.btnPlus).setOnClickListener {
                a.quantite++; maj()
            }
            v.findViewById<Button>(R.id.btnSuppr).setOnClickListener {
                articles.removeAt(i); maj()
            }
            return v
        }
    }
}
