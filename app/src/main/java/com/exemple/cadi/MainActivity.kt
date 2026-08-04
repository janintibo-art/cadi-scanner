package com.exemple.cadi

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
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
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.google.android.material.snackbar.Snackbar
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var adapter: ArticleAdapter
    private lateinit var totalView: TextView
    private lateinit var budgetView: TextView
    private lateinit var racine: View
    private lateinit var feedback: Feedback
    private var photoFile: File? = null
    private var alerteDejaJouee = false

    private val prendrePhoto =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
            if (ok) photoFile?.let { lirePrix(it) }
        }

    private val ouvrirScan =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { maj() }

    private val demanderLocalisation =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        Caddie.init(this)
        Historique.init(this)
        MemoirePrix.init(this)
        ListeCourses.init(this)
        feedback = Feedback(this)

        racine = findViewById(R.id.racine)
        totalView = findViewById(R.id.total)
        budgetView = findViewById(R.id.budget)
        adapter = ArticleAdapter()
        findViewById<ListView>(R.id.liste).adapter = adapter

        findViewById<Button>(R.id.btnScanContinu).setOnClickListener {
            ouvrirScan.launch(Intent(this, ScanActivity::class.java))
        }
        findViewById<Button>(R.id.btnPhoto).setOnClickListener { lancerCamera() }
        findViewById<Button>(R.id.btnManuel).setOnClickListener { saisieManuelle(null, null) }
        findViewById<Button>(R.id.btnVider).setOnClickListener { confirmerVider() }
        findViewById<Button>(R.id.btnValider).setOnClickListener { validerCourses() }
        findViewById<Button>(R.id.btnHistorique).setOnClickListener {
            startActivity(Intent(this, HistoriqueActivity::class.java))
        }
        findViewById<Button>(R.id.btnExport).setOnClickListener { menuExport() }
        findViewById<Button>(R.id.btnListe).setOnClickListener {
            startActivity(Intent(this, ListeActivity::class.java))
        }
        budgetView.setOnClickListener { definirBudget() }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) demanderLocalisation.launch(Manifest.permission.ACCESS_COARSE_LOCATION)

        maj()
    }

    override fun onResume() {
        super.onResume()
        maj()
    }

    // ---------- Budget ----------

    private fun definirBudget() {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            hint = "Ex : 60"
            if (Caddie.budget > 0) setText(String.format(Locale.FRANCE, "%.2f", Caddie.budget))
        }
        AlertDialog.Builder(this)
            .setTitle("Budget maximum")
            .setMessage("Le total passera en orange à 80 % puis en rouge au dépassement.")
            .setView(input)
            .setPositiveButton("Valider") { _, _ ->
                Caddie.budget = input.text.toString().replace(',', '.').toDoubleOrNull() ?: 0.0
                alerteDejaJouee = false
                maj()
            }
            .setNeutralButton("Supprimer") { _, _ -> Caddie.budget = 0.0; maj() }
            .setNegativeButton("Annuler", null)
            .show()
    }

    // ---------- OCR de l'etiquette ----------

    private fun lancerCamera() {
        val f = File(cacheDir, "etiquette.jpg")
        photoFile = f
        val uri: Uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", f)
        prendrePhoto.launch(uri)
    }

    private fun lirePrix(fichier: File) {
        val img = try {
            InputImage.fromFilePath(this, Uri.fromFile(fichier))
        } catch (e: Exception) {
            return toast("Photo illisible")
        }
        toast("Analyse en cours…")
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS).process(img)
            .addOnSuccessListener { traiterTexte(it.text) }
            .addOnFailureListener { toast("Erreur d'analyse") }
    }

    private fun traiterTexte(texte: String) {
        val prix = EtiquetteParser.prix(texte)
        val promo = EtiquetteParser.promo(texte)

        if (prix.isEmpty()) {
            feedback.echec()
            saisieManuelle(null, null)
            return
        }
        feedback.succes()

        // Un seul prix unitaire et pas d'ambiguite : on enchaine directement
        val principal = EtiquetteParser.prixPrincipal(prix)
        if (prix.size == 1 && principal != null) {
            demanderQuantite(principal.valeur, "Article", null, promo)
            return
        }

        // Sinon on montre la nature de chaque montant lu (unite, kilo, litre)
        val titre = promo?.let { "Promo détectée : ${it.libelle}" } ?: "Quel est le prix ?"
        val libelles = prix.map { it.libelle }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle(titre)
            .setMessage("Choisissez le prix de vente (les prix au kg ou au L servent seulement à comparer)")
            .setItems(libelles) { _, i ->
                demanderQuantite(prix[i].valeur, "Article", null, promo)
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    // ---------- Saisie ----------

    private fun demanderQuantite(
        prix: Double, nom: String, code: String?, promo: Promo? = null
    ) {
        val qteDepart = promo?.quantiteConseillee ?: 1
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(qteDepart.toString()); setSelection(length())
        }

        val message = if (promo != null) {
            val t = promo.totalPour(qteDepart, prix)
            "Offre : ${promo.libelle}\n" +
                "Pour $qteDepart articles vous payez ${fmt(t)} " +
                "au lieu de ${fmt(prix * qteDepart)}.\n\nQuantité :"
        } else "Quantité :"

        AlertDialog.Builder(this)
            .setTitle("$nom — ${fmt(prix)}")
            .setMessage(message)
            .setView(input)
            .setPositiveButton("Ajouter") { _, _ ->
                val q = input.text.toString().toIntOrNull() ?: 1
                Caddie.ajouter(prix, if (q < 1) 1 else q, nom, code, promo)
                maj(); annulable()
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    private fun saisieManuelle(nom: String?, code: String?) {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            hint = "Ex : 2,49"
        }
        AlertDialog.Builder(this)
            .setTitle(nom ?: "Prix de l'article")
            .setView(input)
            .setPositiveButton("OK") { _, _ ->
                val p = input.text.toString().replace(',', '.').toDoubleOrNull()
                if (p != null && p > 0) demanderQuantite(p, nom ?: "Article", code)
                else toast("Prix invalide")
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    private fun confirmerVider() {
        if (Caddie.articles.isEmpty()) return
        AlertDialog.Builder(this)
            .setTitle("Vider le caddie ?")
            .setPositiveButton("Oui") { _, _ -> Caddie.vider(); maj(); annulable() }
            .setNegativeButton("Non", null)
            .show()
    }

    // ---------- Fin de courses / export ----------

    private fun validerCourses() {
        if (Caddie.articles.isEmpty()) return toast("Le caddie est vide")

        val input = EditText(this).apply {
            hint = "Nom du magasin (facultatif)"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
        }
        AlertDialog.Builder(this)
            .setTitle("Valider les courses")
            .setMessage("Total ${fmt(Caddie.total)} — la liste sera archivée puis le caddie vidé.")
            .setView(input)
            .setPositiveButton("Archiver") { _, _ ->
                val magasin = input.text.toString()
                MemoirePrix.enregistrerCourse(Caddie.articles, magasin)
                Historique.archiver(magasin)
                Caddie.vider()
                ListeCourses.viderCoches()
                maj()
                toast("Course archivée — ${MemoirePrix.nbReleves} prix mémorisés")
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    private fun menuExport() {
        if (Caddie.articles.isEmpty()) return toast("Le caddie est vide")
        AlertDialog.Builder(this)
            .setTitle("Exporter la liste")
            .setItems(arrayOf("Envoyer en texte", "Fichier CSV (tableur)")) { _, i ->
                if (i == 0) Export.partagerTexte(
                    this,
                    Export.texte(Caddie.articles, Caddie.total, Caddie.economies),
                    "Mes courses"
                ) else Export.partagerCsv(
                    this,
                    Export.csv(Caddie.articles, Caddie.total),
                    "courses.csv"
                )
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    // ---------- Annulation ----------

    private fun annulable() {
        val libelle = Caddie.libelleAnnulation() ?: return
        Snackbar.make(racine, libelle, Snackbar.LENGTH_LONG)
            .setAction("ANNULER") {
                if (Caddie.annuler()) { maj(); toast("Annulé") }
            }
            .show()
    }

    // ---------- Affichage ----------

    private fun maj() {
        majRappelListe()
        totalView.text = "${fmt(Caddie.total)}   •   ${Caddie.nbArticles} article${if (Caddie.nbArticles > 1) "s" else ""}"

        val ratio = Caddie.ratioBudget
        when {
            ratio == null -> {
                totalView.setBackgroundColor(0xFF1B5E20.toInt())
                budgetView.text = if (Caddie.economies > 0.005)
                    "💰 ${fmt(Caddie.economies)} économisés — 🎯 définir un budget"
                else "🎯 Définir un budget"
            }
            ratio >= 1.0 -> {
                totalView.setBackgroundColor(0xFFB71C1C.toInt())
                budgetView.text = "⚠️ Budget ${fmt(Caddie.budget)} dépassé de ${fmt(Caddie.total - Caddie.budget)}"
                if (!alerteDejaJouee) { feedback.alerte(); alerteDejaJouee = true }
            }
            ratio >= 0.8 -> {
                totalView.setBackgroundColor(0xFFE65100.toInt())
                budgetView.text = "Budget ${fmt(Caddie.budget)} — reste ${fmt(Caddie.budget - Caddie.total)}"
                alerteDejaJouee = false
            }
            else -> {
                totalView.setBackgroundColor(0xFF1B5E20.toInt())
                budgetView.text = "Budget ${fmt(Caddie.budget)} — reste ${fmt(Caddie.budget - Caddie.total)}"
                alerteDejaJouee = false
            }
        }
        adapter.notifyDataSetChanged()
    }

    /** Affiche ce qu'il reste a prendre si une liste est en cours. */
    private fun majRappelListe() {
        val rappel = findViewById<TextView>(R.id.rappelListe)
        val restants = ListeCourses.restants
        when {
            ListeCourses.items.isEmpty() -> rappel.visibility = View.GONE
            restants == 0 -> {
                rappel.visibility = View.VISIBLE
                rappel.text = "✅ Liste terminée"
                rappel.setBackgroundColor(0xFFC8E6C9.toInt())
            }
            else -> {
                rappel.visibility = View.VISIBLE
                val noms = ListeCourses.items.filter { !it.coche }.take(3).joinToString(", ") { it.nom }
                rappel.text = "📝 Reste $restants : $noms" + if (restants > 3) "…" else ""
                rappel.setBackgroundColor(0xFFFFF9C4.toInt())
            }
        }
    }

    private fun fmt(v: Double) = String.format(Locale.FRANCE, "%.2f €", v)
    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    override fun onDestroy() {
        super.onDestroy()
        feedback.liberer()
    }

    // ---------- Liste ----------

    inner class ArticleAdapter : BaseAdapter() {
        override fun getCount() = Caddie.articles.size
        override fun getItem(i: Int) = Caddie.articles[i]
        override fun getItemId(i: Int) = i.toLong()

        override fun getView(i: Int, convertView: View?, parent: ViewGroup): View {
            val v = convertView ?: layoutInflater.inflate(R.layout.item_article, parent, false)
            val a = Caddie.articles[i]

            v.findViewById<TextView>(R.id.nom).text = a.nom
            v.findViewById<TextView>(R.id.ligne).text =
                "${fmt(a.prix)} × ${a.quantite} = ${fmt(a.total)}"

            val vuePromo = v.findViewById<TextView>(R.id.promo)
            if (a.promo != null) {
                vuePromo.visibility = View.VISIBLE
                vuePromo.text = "🏷️ ${a.promo}" +
                    if (a.economie > 0.005) "  −${fmt(a.economie)}" else ""
            } else vuePromo.visibility = View.GONE
            v.findViewById<TextView>(R.id.qte).text = a.quantite.toString()

            v.findViewById<Button>(R.id.btnMoins).setOnClickListener {
                Caddie.changerQuantite(i, -1); maj(); annulable()
            }
            v.findViewById<Button>(R.id.btnPlus).setOnClickListener {
                Caddie.changerQuantite(i, +1); maj()
            }
            v.findViewById<Button>(R.id.btnSuppr).setOnClickListener {
                Caddie.supprimer(i); maj(); annulable()
            }
            return v
        }
    }
}
