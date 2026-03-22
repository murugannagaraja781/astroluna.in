import java.net.URL
import javax.net.ssl.HttpsURLConnection

fun main() {
    try {
        val url = URL("https://astroluna.in/api/rasi-eng/horoscope/daily")
        val conn = url.openConnection() as HttpsURLConnection
        conn.requestMethod = "GET"
        conn.connect()
        println("Response code: ${conn.responseCode}")
        val body = conn.inputStream.bufferedReader().use { it.readText() }
        println("Body size: ${body.length}")
        println("First 100 chars: ${body.take(100)}")
    } catch (e: Exception) {
        println("Connection error: ${e::class.simpleName}")
        println("Message: ${e.message}")
    }
}
main()
