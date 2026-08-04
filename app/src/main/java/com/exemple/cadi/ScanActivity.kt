package com.exemple.cadi

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Scan continu : la camera reste ouverte, on enchaine les articles.
 * L'analyse est mise en pause pendant la boite de dialogue puis reprend.
 */
class ScanActivity : AppCompatActivity() {

    private lateinit var apercu: PreviewView
    private lateinit var bandeauTotal: EtiquetteTotal
    private lateinit var bandeauInfo: TextView
    private lateinit var feedback: Feedback

    private val scanner = BarcodeScanning.getClient()
    private val analyse = Executors.newSingleThreadExecutor()
    private val reseau = Executors.newSingleThreadExecutor()
    private val ui = Handler(Looper.getMainLooper())

    /** True quand une boite de dialogue est ouverte : on ignore les images. */
    @Volatile private var enPause = false

    /** Evite de re-scanner 20 fois le meme code d'affilee. */
    private var dernierCode: String? = null
    private var dernierInstant = 0L

    private val demanderCamera =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
            if (ok) demarrerCamera()
            else {
                Toast.makeText(this, "Accès caméra refusé", Toast.LENGTH_LONG).show()
                finish()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scan)
        Caddie.init(this)
        Historique.init(this)
        MemoirePrix.init(this)
        ListeCourses.init(this)
        feedback = Feedback(this)

        apercu = findViewById(R.id.apercu)
        bandeauTotal = findViewById(R.id.scanTotal)
        bandeauInfo = findViewById(R.id.scanInfo)

        animerTrait()
        findViewById<Button>(R.id.btnTermine).setOnClickListener { finish() }
        findViewById<Button>(R.id.btnSaisie).setOnClickListener {
            enPause = true
            saisirPrix("Article", null, null)
        }

        majBandeau()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) demarrerCamera()
        else demanderCamera.launch(Manifest.permission.CAMERA)
    }

    // ---------- Camera ----------

    private fun demarrerCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            val provider = future.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(apercu.surfaceProvider)
            }

            val analyseur = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { it.setAnalyzer(analyse, ::analyser) }

            try {
                provider.unbindAll()
                provider.bindToLifecycle(
                    this, CameraSelector.DEFAULT_BACK_CAMERA, preview, analyseur
                )
            } catch (e: Exception) {
                Toast.makeText(this, "Caméra indisponible", Toast.LENGTH_LONG).show()
                finish()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    @SuppressLint("UnsafeOptInUsageError")
    @ExperimentalGetImage
    private fun analyser(proxy: androidx.camera.core.ImageProxy) {
        val media = proxy.image
        if (media == null || enPause) { proxy.close(); return }

        val img = InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)
        scanner.process(img)
            .addOnSuccessListener { codes ->
                val code = codes.firstNotNullOfOrNull { it.rawValue }
                if (!code.isNullOrBlank()) traiterCode(code)
            }
            .addOnCompleteListener { proxy.close() }
    }

    // ---------- Traitement d'un code ----------

    private fun traiterCode(code: String) {
        val maintenant = System.currentTimeMillis()
        // Anti-rebond : meme code ignore pendant 3 secondes
        if (code == dernierCode && maintenant - dernierInstant < 3000) return
        dernierCode = code
        dernierInstant = maintenant

        enPause = true
        ui.post {
            feedback.succes()
            infoAvecFondu("Code $code — recherche du prix…")
        }

        reseau.execute {
            val res = PriceApi.comparer(code)
            ui.post { proposer(code, res) }
        }
    }

    private fun proposer(code: String, c: Comparatif) {
        val nom = c.nomProduit ?: "Produit $code"

        // Ce que l'utilisateur a lui-meme paye par le passe : source la plus fiable
        val perso = MemoirePrix.synthese(code, nom)
        val resumePerso = perso.resume()

        // Coche automatiquement l'article s'il figure dans la liste de courses
        val coche = ListeCourses.cocherParCode(code) ?: ListeCourses.cocherParNom(nom)

        val infos = mutableListOf<String>()
        coche?.let { infos.add("✅ « $it » coché dans votre liste") }
        resumePerso?.let { infos.add("🧠 $it") }

        if (c.releves.isEmpty()) {
            if (resumePerso == null) feedback.echec() else feedback.succes()
            bandeauInfo.text = if (coche != null) "$nom — coché" else "$nom"
            infos.add("Aucun relevé communautaire pour ce produit.")
            saisirPrix(
                nom, code,
                perso.dernier?.prix,
                infos.joinToString("\n\n")
            )
            return
        }

        val moyenne = c.moyenne!!
        val mini = c.moinsCher!!
        infos.add(
            "🌍 Open Prices : moyenne ${fmt(moyenne)} • mini ${fmt(mini.prix)}" +
                if (mini.magasin.isNotBlank()) " (${mini.magasin})" else ""
        )

        bandeauInfo.text = "$nom — moyenne ${fmt(moyenne)}"
        saisirPrix(
            nom, code,
            perso.dernier?.prix ?: mini.prix,
            infos.joinToString("\n\n")
        )
    }

    /** Boite de saisie prix + quantite, puis reprise du scan. */
    private fun saisirPrix(nom: String, code: String?, suggestion: Double?, info: String? = null) {
        val vue = layoutInflater.inflate(R.layout.dialog_ajout, null)
        val champPrix = vue.findViewById<EditText>(R.id.champPrix)
        val champQte = vue.findViewById<EditText>(R.id.champQte)
        val texteInfo = vue.findViewById<TextView>(R.id.infoPrix)

        champPrix.inputType =
            InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        suggestion?.let { champPrix.setText(String.format(Locale.FRANCE, "%.2f", it)) }
        champQte.setText("1")
        texteInfo.text = info ?: "Saisissez le prix affiché en rayon"

        AlertDialog.Builder(this)
            .setTitle(nom)
            .setView(vue)
            .setCancelable(false)
            .setPositiveButton("Ajouter") { _, _ ->
                val p = champPrix.text.toString().replace(',', '.').toDoubleOrNull()
                val q = champQte.text.toString().toIntOrNull() ?: 1
                if (p != null && p > 0) {
                    Caddie.ajouter(p, if (q < 1) 1 else q, nom, code)
                    majBandeau()
                    // Prevenir si le prix a nettement augmente par rapport a l'habitude
                    MemoirePrix.synthese(code, nom).alerteHausse(p)?.let {
                        Toast.makeText(this, it, Toast.LENGTH_LONG).show()
                    }
                } else {
                    Toast.makeText(this, "Prix ignoré", Toast.LENGTH_SHORT).show()
                }
                reprendre()
            }
            .setNegativeButton("Passer") { _, _ -> reprendre() }
            .setNeutralButton("🌍 Contribuer") { _, _ ->
                val p = champPrix.text.toString().replace(',', '.').toDoubleOrNull()
                if (p != null && p > 0)
                    Export.contribuerOpenPrices(this, code, p, "")
                else
                    Toast.makeText(this, "Saisissez d'abord le prix", Toast.LENGTH_SHORT).show()
                reprendre()
            }
            .show()
    }

    private fun reprendre() {
        enPause = false
        infoAvecFondu("Visez un code-barres")
    }

    /** Change le texte d'information avec un petit fondu. */
    private fun infoAvecFondu(texte: String) {
        bandeauInfo.animate().alpha(0f).setDuration(110).withEndAction {
            bandeauInfo.text = texte
            bandeauInfo.animate().alpha(1f).setDuration(160).start()
        }.start()
    }

    // ---------- Bandeau ----------

    private fun majBandeau() {
        val nb = Caddie.nbArticles
        val legende = when {
            nb == 0 -> "Caddie vide"
            Caddie.economies > 0.005 ->
                "$nb article${if (nb > 1) "s" else ""}  ·  ${fmt(Caddie.economies)} économisés"
            else -> "$nb article${if (nb > 1) "s" else ""}"
        }
        bandeauTotal.afficher(Caddie.total, Caddie.budget, legende)

        val ratio = Caddie.ratioBudget
        if (ratio != null && ratio >= 1.0) feedback.alerte()
    }

    /** Trait lumineux qui balaye la zone de visee, comme une caisse de supermarche. */
    private fun animerTrait() {
        val trait = findViewById<android.view.View>(R.id.traitScan)
        val zone = findViewById<android.view.View>(R.id.zoneVisee)
        zone.post {
            val amplitude = zone.height / 2f - 6f
            android.animation.ObjectAnimator.ofFloat(
                trait, "translationY", -amplitude, amplitude
            ).apply {
                duration = 1600
                repeatMode = android.animation.ValueAnimator.REVERSE
                repeatCount = android.animation.ValueAnimator.INFINITE
                interpolator = android.view.animation.AccelerateDecelerateInterpolator()
                start()
            }
        }
    }

    private fun fmt(v: Double) = String.format(Locale.FRANCE, "%.2f €", v)

    override fun onDestroy() {
        super.onDestroy()
        feedback.liberer()
        analyse.shutdown()
        reseau.shutdown()
    }
}
