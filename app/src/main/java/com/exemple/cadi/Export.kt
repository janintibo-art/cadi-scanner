package com.exemple.cadi

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Generation des exports texte et CSV, puis partage via les applis du telephone. */
object Export {

    private fun euro(v: Double) = String.format(Locale.FRANCE, "%.2f", v)

    /** Liste lisible, adaptee a un envoi WhatsApp ou SMS. */
    fun texte(articles: List<Article>, total: Double, economies: Double): String =
        buildString {
            append("🛒 Mes courses — ")
            append(SimpleDateFormat("dd/MM/yyyy", Locale.FRANCE).format(Date()))
            append("\n\n")
            articles.forEach { a ->
                append("• ${a.nom}\n")
                append("   ${euro(a.prix)} € × ${a.quantite} = ${euro(a.total)} €")
                a.promo?.let { append("  (${it})") }
                append("\n")
            }
            append("\nTOTAL : ${euro(total)} €")
            if (economies > 0.005) append("\nÉconomies promos : ${euro(economies)} €")
            append("\n\n(Cadi Scanner)")
        }

    /** CSV ouvrable dans un tableur. Separateur point-virgule pour Excel FR. */
    fun csv(articles: List<Article>, total: Double): String = buildString {
        append("Article;Prix unitaire;Quantite;Promotion;Total ligne\n")
        articles.forEach { a ->
            append("\"${a.nom.replace("\"", "'")}\";")
            append("${euro(a.prix)};")
            append("${a.quantite};")
            append("\"${a.promo ?: ""}\";")
            append("${euro(a.total)}\n")
        }
        append(";;;TOTAL;${euro(total)}\n")
    }

    /** CSV de tout l'historique, une ligne par course. */
    fun csvHistorique(courses: List<Course>): String = buildString {
        append("Date;Magasin;Nombre d'articles;Total\n")
        courses.sortedBy { it.horodatage }.forEach { c ->
            append("${c.dateCourte};\"${c.magasin}\";${c.nbArticles};${euro(c.total)}\n")
        }
    }

    /**
     * Ouvre Open Prices pour contribuer un prix releve.
     * Le detail est copie dans le presse-papier pour etre colle dans le formulaire.
     * (L'envoi direct par l'API demandera un compte Open Food Facts et une
     * photo de preuve : voir le README.)
     */
    fun contribuerOpenPrices(ctx: Context, code: String?, prix: Double, magasin: String) {
        val resume = buildString {
            append("Code-barres : ${code ?: "sans code"}\n")
            append("Prix : ${euro(prix)} €\n")
            append("Magasin : ${magasin.ifBlank { "à préciser" }}\n")
            append("Date : ${SimpleDateFormat("yyyy-MM-dd", Locale.FRANCE).format(Date())}")
        }
        try {
            val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            cm.setPrimaryClip(android.content.ClipData.newPlainText("Relevé de prix", resume))
        } catch (_: Exception) { }

        try {
            ctx.startActivity(
                Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://prices.openfoodfacts.org"))
            )
        } catch (e: Exception) {
            partagerTexte(ctx, resume, "Relevé de prix")
        }
    }

    /** Partage un texte brut (messagerie, notes...). */
    fun partagerTexte(ctx: Context, contenu: String, titre: String) {
        val i = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, titre)
            putExtra(Intent.EXTRA_TEXT, contenu)
        }
        ctx.startActivity(Intent.createChooser(i, titre))
    }

    /** Ecrit un fichier CSV dans le cache et le partage en piece jointe. */
    fun partagerCsv(ctx: Context, contenu: String, nomFichier: String) {
        try {
            val f = File(ctx.cacheDir, nomFichier)
            // BOM UTF-8 pour que les accents s'affichent correctement dans Excel
            f.writeText("\uFEFF$contenu", Charsets.UTF_8)
            val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", f)

            val i = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, nomFichier)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            ctx.startActivity(Intent.createChooser(i, "Exporter en CSV"))
        } catch (e: Exception) {
            partagerTexte(ctx, contenu, nomFichier)
        }
    }
}
