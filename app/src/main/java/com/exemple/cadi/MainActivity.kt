package com.exemple.cadi

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale
import java.util.concurrent.Executors

data class Article(
    var prix: Double,
    var quantite: Int,
    var nom: String = "Article",
    var code: String? = null
)

class MainActivity : AppCompatActivity() {

    private val articles = mutableListOf<Article>()
    private lateinit var adapter: ArticleAdapter
    private lateinit var totalView: TextView
    private var photoFile: File? = null
    private var modeCodeBarres = false

    private val executor = Executors.newSingleThreadExecutor()
    private val ui = Handler(Looper.getMainLooper())

    /** Rayon de recherche des magasins autour de soi, en km. */
    private var rayonKm = 20.0

    private val prendrePhoto =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
            if (ok) photoFile?.let {
                if (modeCodeBarres) lireCodeBarres(it) else lirePrix(it)
            }
        }

    private val demanderLocalisation =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        totalView = findViewById(R.id.total)
        adapter = ArticleAdapter()
        findViewById<ListView>(R.id.liste).adapter = adapter

        findViewById<Button>(R.id.btnCodeBarres).setOnClickListener {
            modeCodeBarres = true; lancerCamera()
        }
        findViewById<Button>(R.id.btnPhoto).setOnClickListener {
            modeCodeBarres = false; lancerCamera()
        }
        findViewById<Button>(R.id.btnManuel).setOnClickListener { saisieManuelle(null, null) }
        findViewById<Button>(R.id.btnVider).setOnClickListener { confirmerVider() }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) demanderLocalisation.launch(Manifest.permission.ACCESS_COARSE_LOCATION)

        charger()
        maj()
    }

    // ---------- Camera ----------

    private fun lancerCamera() {
        val f = File(cacheDir, "capture.jpg")
        photoFile = f
        val uri: Uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", f)
        prendrePhoto.launch(uri)
    }

    private fun image(fichier: File): InputImage? = try {
        InputImage.fromFilePath(this, Uri.fromFile(fichier))
    } catch (e: Exception) {
        null
    }

    // ---------- Lecture du code-barres ----------

    private fun lireCodeBarres(fichier: File) {
        val img = image(fichier) ?: return toast("Photo illisible")
        toast("Lecture du code-barres…")
        BarcodeScanning.getClient().process(img)
            .addOnSuccessListener { codes ->
                val code = codes.firstNotNullOfOrNull { it.rawValue }
                if (code.isNullOrBlank()) {
                    toast("Code-barres non détecté — cadrez bien le code")
                } else {
                    chercherComparatif(code)
                }
            }
            .addOnFailureListener { toast("Erreur de lecture") }
    }

    // ---------- Comparatif de prix ----------

    private fun chercherComparatif(code: String) {
        val dlg = AlertDialog.Builder(this)
            .setTitle("Recherche…")
            .setMessage("Interrogation d'Open Prices pour le code $code")
            .setCancelable(false)
            .show()

        executor.execute {
            val res = PriceApi.comparer(code)
            val pos = positionApprox()
            ui.post {
                dlg.dismiss()
                afficherComparatif(code, res, pos)
            }
        }
    }

    private fun afficherComparatif(code: String, c: Comparatif, pos: Location?) {
        val nom = c.nomProduit ?: "Produit $code"

        if (c.releves.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle(nom)
                .setMessage(
                    "Aucun relevé de prix connu pour ce produit dans la base " +
                        "collaborative Open Prices.\n\nSaisissez le prix du magasin : " +
                        "vous pourrez ensuite le partager sur prices.openfoodfacts.org " +
                        "pour aider les autres."
                )
                .setPositiveButton("Saisir le prix") { _, _ -> saisieManuelle(nom, code) }
                .setNegativeButton("Annuler", null)
                .show()
            return
        }

        val moyenne = c.moyenne!!
        val mini = c.moinsCher!!
        val proche = c.moinsCherAutour(pos, rayonKm)

        val texte = buildString {
            append("Prix moyen relevé : ${fmt(moyenne)}\n")
            append("(sur ${c.releves.size} relevé${if (c.releves.size > 1) "s" else ""})\n\n")

            append("💰 Le moins cher connu : ${fmt(mini.prix)}\n")
            append("   ${mini.magasin}")
            if (mini.ville.isNotBlank()) append(" — ${mini.ville}")
            if (mini.date.isNotBlank()) append("\n   relevé le ${mini.date}")
            append("\n\n")

            when {
                pos == null ->
                    append("📍 Position indisponible : impossible de filtrer les magasins autour de vous.")
                proche == null ->
                    append("📍 Aucun relevé dans un rayon de ${rayonKm.toInt()} km autour de vous.")
                else -> {
                    val d = proche.distanceKm(pos)
                    append("📍 Le moins cher autour de vous : ${fmt(proche.prix)}\n")
                    append("   ${proche.magasin}")
                    if (proche.ville.isNotBlank()) append(" — ${proche.ville}")
                    d?.let { append("\n   à ${String.format(Locale.FRANCE, "%.1f", it)} km") }
                }
            }
            append("\n\n⚠️ Prix collaboratifs (Open Prices, ODbL), parfois anciens : à vérifier en rayon.")
        }

        AlertDialog.Builder(this)
            .setTitle(nom)
            .setMessage(texte)
            .setPositiveButton("Ajouter au caddie") { _, _ ->
                saisieManuelle(nom, code, suggestion = mini.prix)
            }
            .setNeutralButton("Tous les relevés") { _, _ -> listeReleves(nom, c, pos) }
            .setNegativeButton("Fermer", null)
            .show()
    }

    private fun listeReleves(nom: String, c: Comparatif, pos: Location?) {
        val lignes = c.releves.sortedBy { it.prix }.map { r ->
            val d = r.distanceKm(pos)?.let { " • ${String.format(Locale.FRANCE, "%.0f", it)} km" } ?: ""
            "${fmt(r.prix)} — ${r.magasin}${if (r.ville.isNotBlank()) " (${r.ville})" else ""}$d"
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("$nom — ${lignes.size} relevés")
            .setItems(lignes, null)
            .setPositiveButton("Fermer", null)
            .show()
    }

    private fun positionApprox(): Location? = try {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) null
        else {
            val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
            lm.getProviders(true)
                .mapNotNull { lm.getLastKnownLocation(it) }
                .maxByOrNull { it.time }
        }
    } catch (e: SecurityException) {
        null
    }

    // ---------- OCR du prix ----------

    private fun lirePrix(fichier: File) {
        val img = image(fichier) ?: return toast("Photo illisible")
        toast("Analyse en cours…")
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS).process(img)
            .addOnSuccessListener { traiterTexte(it.text) }
            .addOnFailureListener { toast("Erreur d'analyse") }
    }

    private fun traiterTexte(texte: String) {
        val regex = Regex("""(\d{1,4})\s*[.,]\s*(\d{2})(?!\d)""")
        val candidats = regex.findAll(texte)
            .map { it.groupValues[1].toInt() + it.groupValues[2].toInt() / 100.0 }
            .filter { it in 0.05..2000.0 }
            .distinct()
            .toList()

        when {
            candidats.isEmpty() -> {
                toast("Aucun prix détecté")
                saisieManuelle(null, null)
            }
            candidats.size == 1 -> demanderQuantite(candidats[0], "Article", null)
            else -> AlertDialog.Builder(this)
                .setTitle("Quel est le prix de l'article ?")
                .setItems(candidats.map { fmt(it) }.toTypedArray()) { _, i ->
                    demanderQuantite(candidats[i], "Article", null)
                }
                .setNegativeButton("Annuler", null)
                .show()
        }
    }

    // ---------- Ajout ----------

    private fun demanderQuantite(prix: Double, nom: String, code: String?) {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText("1"); setSelection(1)
        }
        AlertDialog.Builder(this)
            .setTitle("$nom — ${fmt(prix)}")
            .setMessage("Quantité :")
            .setView(input)
            .setPositiveButton("Ajouter") { _, _ ->
                val q = input.text.toString().toIntOrNull() ?: 1
                ajouter(prix, if (q < 1) 1 else q, nom, code)
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    private fun saisieManuelle(nom: String?, code: String?, suggestion: Double? = null) {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            hint = "Ex : 2,49"
            suggestion?.let { setText(String.format(Locale.FRANCE, "%.2f", it)) }
        }
        AlertDialog.Builder(this)
            .setTitle(nom ?: "Prix de l'article")
            .setMessage("Prix affiché en rayon :")
            .setView(input)
            .setPositiveButton("OK") { _, _ ->
                val p = input.text.toString().replace(',', '.').toDoubleOrNull()
                if (p != null && p > 0) demanderQuantite(p, nom ?: "Article", code)
                else toast("Prix invalide")
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    private fun ajouter(prix: Double, quantite: Int, nom: String, code: String?) {
        val existant = articles.find { it.prix == prix && it.nom == nom }
        if (existant != null) existant.quantite += quantite
        else articles.add(0, Article(prix, quantite, nom, code))
        maj()
        toast("Ajouté : $quantite × ${fmt(prix)}")
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
    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    private fun sauver() {
        val arr = JSONArray()
        articles.forEach {
            arr.put(
                JSONObject().put("p", it.prix).put("q", it.quantite)
                    .put("n", it.nom).put("c", it.code ?: "")
            )
        }
        getSharedPreferences("cadi", MODE_PRIVATE).edit()
            .putString("articles", arr.toString()).apply()
    }

    private fun charger() {
        try {
            val s = getSharedPreferences("cadi", MODE_PRIVATE)
                .getString("articles", null) ?: return
            val arr = JSONArray(s)
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

    // ---------- Liste ----------

    inner class ArticleAdapter : BaseAdapter() {
        override fun getCount() = articles.size
        override fun getItem(i: Int) = articles[i]
        override fun getItemId(i: Int) = i.toLong()

        override fun getView(i: Int, convertView: View?, parent: ViewGroup): View {
            val v = convertView ?: layoutInflater.inflate(R.layout.item_article, parent, false)
            val a = articles[i]

            v.findViewById<TextView>(R.id.nom).text = a.nom
            v.findViewById<TextView>(R.id.ligne).text =
                "${fmt(a.prix)} × ${a.quantite} = ${fmt(a.prix * a.quantite)}"
            v.findViewById<TextView>(R.id.qte).text = a.quantite.toString()

            v.findViewById<Button>(R.id.btnMoins).setOnClickListener {
                if (a.quantite > 1) a.quantite-- else articles.removeAt(i)
                maj()
            }
            v.findViewById<Button>(R.id.btnPlus).setOnClickListener { a.quantite++; maj() }
            v.findViewById<Button>(R.id.btnSuppr).setOnClickListener { articles.removeAt(i); maj() }
            return v
        }
    }
}
