package com.exemple.cadi

import android.location.Location
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Un relevé de prix trouvé dans Open Prices. */
data class Releve(
    val prix: Double,
    val devise: String,
    val date: String,
    val magasin: String,
    val ville: String,
    val lat: Double?,
    val lon: Double?
) {
    /** Distance en km depuis la position donnée, ou null si inconnue. */
    fun distanceKm(depuis: Location?): Double? {
        if (depuis == null || lat == null || lon == null) return null
        val res = FloatArray(1)
        Location.distanceBetween(depuis.latitude, depuis.longitude, lat, lon, res)
        return res[0] / 1000.0
    }
}

/** Resultat de la comparaison pour un code-barres. */
data class Comparatif(
    val nomProduit: String?,
    val releves: List<Releve>
) {
    val moyenne: Double? get() = releves.takeIf { it.isNotEmpty() }?.map { it.prix }?.average()
    val moinsCher: Releve? get() = releves.minByOrNull { it.prix }

    /** Le moins cher parmi les magasins situes dans le rayon donne. */
    fun moinsCherAutour(pos: Location?, rayonKm: Double): Releve? =
        releves.filter { r -> r.distanceKm(pos)?.let { it <= rayonKm } == true }
            .minByOrNull { it.prix }
}

object PriceApi {

    // Open Food Facts demande un User-Agent identifiant l'application
    private const val UA = "CadiScanner/1.0 (Android)"

    private fun get(url: String): String? = try {
        (URL(url).openConnection() as HttpURLConnection).run {
            requestMethod = "GET"
            setRequestProperty("User-Agent", UA)
            connectTimeout = 8000
            readTimeout = 8000
            if (responseCode == 200) inputStream.bufferedReader().use { it.readText() } else null
        }
    } catch (e: Exception) {
        null
    }

    /** Nom commercial du produit, via Open Food Facts. */
    private fun nomProduit(code: String): String? {
        val json = get(
            "https://world.openfoodfacts.org/api/v2/product/$code.json" +
                "?fields=product_name,product_name_fr,brands,quantity"
        ) ?: return null
        return try {
            val o = JSONObject(json)
            if (o.optInt("status") != 1) return null
            val p = o.optJSONObject("product") ?: return null
            val nom = p.optString("product_name_fr").ifBlank { p.optString("product_name") }
            if (nom.isBlank()) return null
            listOf(p.optString("brands"), nom, p.optString("quantity"))
                .filter { it.isNotBlank() }
                .joinToString(" ")
        } catch (e: Exception) {
            null
        }
    }

    /** Releves de prix, via Open Prices (donnees collaboratives, licence ODbL). */
    private fun releves(code: String): List<Releve> {
        val json = get(
            "https://prices.openfoodfacts.org/api/v1/prices" +
                "?product_code=$code&order_by=-date&size=100"
        ) ?: return emptyList()
        return try {
            val items = JSONObject(json).optJSONArray("items") ?: return emptyList()
            (0 until items.length()).mapNotNull { i ->
                val o = items.optJSONObject(i) ?: return@mapNotNull null
                val prix = o.optDouble("price", Double.NaN)
                if (prix.isNaN() || prix <= 0) return@mapNotNull null
                val loc = o.optJSONObject("location")
                Releve(
                    prix = prix,
                    devise = o.optString("currency", "EUR"),
                    date = o.optString("date", ""),
                    magasin = loc?.optString("osm_name").orEmpty().ifBlank { "Magasin inconnu" },
                    ville = loc?.optString("osm_address_city").orEmpty(),
                    lat = loc?.optDouble("osm_lat", Double.NaN)?.takeIf { !it.isNaN() },
                    lon = loc?.optDouble("osm_lon", Double.NaN)?.takeIf { !it.isNaN() }
                )
            }.filter { it.devise == "EUR" }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** A appeler depuis un thread de fond. */
    fun comparer(code: String): Comparatif = Comparatif(nomProduit(code), releves(code))
}
