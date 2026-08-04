package com.exemple.cadi

import java.util.Locale

/** Nature d'un montant lu sur une etiquette. */
enum class TypePrix { UNITAIRE, AU_KILO, AU_LITRE, INCONNU }

data class PrixLu(val valeur: Double, val type: TypePrix) {
    val libelle: String
        get() = String.format(Locale.FRANCE, "%.2f €", valeur) + when (type) {
            TypePrix.AU_KILO -> " / kg"
            TypePrix.AU_LITRE -> " / L"
            TypePrix.UNITAIRE -> " (unité)"
            TypePrix.INCONNU -> ""
        }
}

/** Une offre promotionnelle detectee sur l'etiquette. */
sealed class Promo {
    abstract val libelle: String

    /** x achetes, y offerts. Ex : 2 achetes = 1 offert. */
    data class LotOffert(val achetes: Int, val offerts: Int) : Promo() {
        override val libelle = "$achetes acheté(s) = $offerts offert(s)"
    }

    /** Le n-ieme article a -p%. Ex : le 2eme a -50%. */
    data class RemiseNieme(val rang: Int, val pourcent: Int) : Promo() {
        override val libelle = "${rang}ᵉ à -$pourcent%"
    }

    /** Lot a prix fixe. Ex : les 3 pour 5 €. */
    data class PackPrix(val nombre: Int, val montant: Double) : Promo() {
        override val libelle = "les $nombre pour ${String.format(Locale.FRANCE, "%.2f €", montant)}"
    }

    /** Remise simple sur tout. Ex : -30%. */
    data class RemiseSimple(val pourcent: Int) : Promo() {
        override val libelle = "-$pourcent% sur le prix"
    }

    /**
     * Prix total reellement paye pour cette quantite, promo appliquee.
     */
    fun totalPour(quantite: Int, prixUnitaire: Double): Double = when (this) {
        is LotOffert -> {
            val groupe = achetes + offerts
            val payants = (quantite / groupe) * achetes + minOf(quantite % groupe, achetes)
            payants * prixUnitaire
        }
        is RemiseNieme -> {
            val remises = quantite / rang
            (quantite - remises) * prixUnitaire + remises * prixUnitaire * (1 - pourcent / 100.0)
        }
        is PackPrix -> {
            val lots = quantite / nombre
            lots * montant + (quantite % nombre) * prixUnitaire
        }
        is RemiseSimple -> quantite * prixUnitaire * (1 - pourcent / 100.0)
    }

    /** Quantite minimale a partir de laquelle l'offre devient interessante. */
    val quantiteConseillee: Int
        get() = when (this) {
            is LotOffert -> achetes + offerts
            is RemiseNieme -> rang
            is PackPrix -> nombre
            is RemiseSimple -> 1
        }
}

/**
 * Extrait d'un texte OCR les prix (en distinguant unite / kilo / litre)
 * et les offres promotionnelles courantes en grande distribution.
 */
object EtiquetteParser {

    private val REGEX_MONTANT = Regex("""(\d{1,4})\s*[.,€]\s*(\d{2})(?!\d)""")

    /** Marqueurs a droite ou a gauche du montant indiquant l'unite de mesure. */
    private val KILO = Regex("""(?i)(€\s*/\s*kg|/\s*kg|le\s+kilo|au\s+kilo|le\s+kg|par\s+kg)""")
    private val LITRE = Regex("""(?i)(€\s*/\s*l\b|/\s*l\b|le\s+litre|au\s+litre)""")

    /** Lit tous les montants du texte avec leur nature probable. */
    fun prix(texte: String): List<PrixLu> {
        val lignes = texte.lines()
        val trouves = mutableListOf<PrixLu>()

        for (ligne in lignes) {
            for (m in REGEX_MONTANT.findAll(ligne)) {
                val valeur = m.groupValues[1].toInt() + m.groupValues[2].toInt() / 100.0
                if (valeur < 0.05 || valeur > 2000.0) continue

                // On regarde ce qui suit immediatement le montant sur la meme ligne
                val apres = ligne.substring(minOf(m.range.last + 1, ligne.length))
                    .take(12)
                val contexte = apres + " " + ligne

                val type = when {
                    KILO.containsMatchIn(apres) -> TypePrix.AU_KILO
                    LITRE.containsMatchIn(apres) -> TypePrix.AU_LITRE
                    KILO.containsMatchIn(contexte) -> TypePrix.AU_KILO
                    LITRE.containsMatchIn(contexte) -> TypePrix.AU_LITRE
                    else -> TypePrix.UNITAIRE
                }
                trouves.add(PrixLu(valeur, type))
            }
        }
        return trouves.distinctBy { it.valeur to it.type }
    }

    /**
     * Le prix a retenir par defaut : le plus gros montant unitaire,
     * car sur une etiquette le prix de vente est en general le plus grand
     * et le prix au kilo sert seulement de comparaison.
     */
    fun prixPrincipal(liste: List<PrixLu>): PrixLu? =
        liste.filter { it.type == TypePrix.UNITAIRE }.maxByOrNull { it.valeur }
            ?: liste.maxByOrNull { it.valeur }

    /** Detecte une offre promotionnelle dans le texte de l'etiquette. */
    fun promo(texte: String): Promo? {
        val t = texte.lowercase()
            .replace('\n', ' ')
            .replace(Regex("""\s+"""), " ")

        // "2 achetes = 1 offert", "2 achetes 1 offert", "1 achete 1 offert"
        Regex("""(\d)\s*achet\w*\s*[=+]?\s*(\d)\s*offert""").find(t)?.let {
            return Promo.LotOffert(it.groupValues[1].toInt(), it.groupValues[2].toInt())
        }
        // "le 2eme a -50%", "2eme a moitie prix", "2e a -50%"
        Regex("""(\d)\s*(?:e|eme|ème|è)\s*(?:a|à)?\s*-?\s*(\d{1,2})\s*%""").find(t)?.let {
            return Promo.RemiseNieme(it.groupValues[1].toInt(), it.groupValues[2].toInt())
        }
        Regex("""(\d)\s*(?:e|eme|ème|è)\s*(?:a|à)?\s*moiti\w*\s*prix""").find(t)?.let {
            return Promo.RemiseNieme(it.groupValues[1].toInt(), 50)
        }
        // "les 3 pour 5,00", "3 pour 5 €", "2 pour 5€"
        Regex("""(?:les\s*)?(\d)\s*pour\s*(\d{1,3})\s*[.,€]\s*(\d{2})?""").find(t)?.let {
            val n = it.groupValues[1].toInt()
            val cents = it.groupValues[3].ifBlank { "00" }.toInt()
            val montant = it.groupValues[2].toInt() + cents / 100.0
            if (n in 2..12 && montant > 0) return Promo.PackPrix(n, montant)
        }
        // "-30%", "30% de remise", "remise 30%"
        Regex("""-\s*(\d{1,2})\s*%""").find(t)?.let {
            val p = it.groupValues[1].toInt()
            if (p in 5..90) return Promo.RemiseSimple(p)
        }
        return null
    }
}

/**
 * Reconstruit une Promo depuis son libelle, pour pouvoir recalculer
 * le total apres un changement de quantite ou un rechargement.
 */
object Promos {
    fun depuisLibelle(libelle: String): Promo? {
        Regex("""(\d+) acheté\(s\) = (\d+) offert""").find(libelle)?.let {
            return Promo.LotOffert(it.groupValues[1].toInt(), it.groupValues[2].toInt())
        }
        Regex("""(\d+)ᵉ à -(\d+)%""").find(libelle)?.let {
            return Promo.RemiseNieme(it.groupValues[1].toInt(), it.groupValues[2].toInt())
        }
        Regex("""les (\d+) pour ([\d,]+)""").find(libelle)?.let {
            val m = it.groupValues[2].replace(',', '.').toDoubleOrNull() ?: return null
            return Promo.PackPrix(it.groupValues[1].toInt(), m)
        }
        Regex("""^-(\d+)% sur le prix""").find(libelle)?.let {
            return Promo.RemiseSimple(it.groupValues[1].toInt())
        }
        return null
    }
}
