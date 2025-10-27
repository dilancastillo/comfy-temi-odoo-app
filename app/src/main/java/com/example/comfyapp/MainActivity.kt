package com.example.comfyapp

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.robotemi.sdk.Robot
import com.robotemi.sdk.TtsRequest
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.lang.Error
import com.example.comfyapp.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity(), Robot.TtsListener {
    private lateinit var robot: Robot
    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var odooApi: OdooApi
    private lateinit var binding: ActivityMainBinding
    private val categoryMap = mapOf(
        "Cemento" to 370,
        "Baño" to 521,
        "Grifería" to 53,
        "Pisos hidráulicos" to 379
    )
    private val locationIdTienda = 8

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        //setContentView(R.layout.activity_main)
        setContentView(binding.root)

        binding.topAppBar.inflateMenu(R.menu.menu_main)
        binding.topAppBar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_run_reposition_now -> {
                    triggerRepositionJobNow()
                    true
                }
                else -> false
            }
        }

        robot = Robot.getInstance()
        robot.addTtsListener(this)

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)

        val retrofit = Retrofit.Builder()
            .baseUrl("https://comfer-staging-24811489.dev.odoo.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        odooApi = retrofit.create(OdooApi::class.java)

        binding.chipCemento.setOnClickListener { filterProducts("Cemento") }
        binding.chipBano.setOnClickListener { filterProducts("Baño") }
        binding.chipGriferia.setOnClickListener { filterProducts("Grifería") }
        binding.chipGriferia.setOnClickListener { filterProducts("Pisos hidráulicos") }
        binding.chipCemento.isChecked = true

        filterProducts("Cemento")

        startListening()

        scheduleDailyReposition()
    }

    fun speak(text: String) {
        robot.speak(TtsRequest.create(text, false))
    }

    private fun startListening() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-ES")
        speechRecognizer.setRecognitionListener(object : android.speech.RecognitionListener {
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                matches?.firstOrNull()?.let { command ->
                    when {
                        command.contains("cemento", ignoreCase = true) -> filterProducts("Cemento")
                        command.contains("baño", ignoreCase = true) -> filterProducts("Baño")
                        command.contains("grifería", ignoreCase = true) -> filterProducts("Grifería")
                        command.contains("pisos_hidraulicos", ignoreCase = true) -> filterProducts("Pisos hidráulicos ")
                        else -> speak("Comando no reconocido. Intenta de nuevo.")
                    }
                }
                startListening()
            }

            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) { startListening() }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        speechRecognizer.startListening(intent)
    }

    private fun filterProducts(category: String) {
        val categoryId = categoryMap[category] ?: return
        val domain = listOf(listOf("categ_id", "=", categoryId))
        val fields = mapOf("id" to true, "name" to true, "list_price" to true, "image_1920" to true, "description_sale" to true)
        OdooHelper.executeOdooRpc(
            "product.product",
            "search_read",
            domain,
            fields = fields,
            onSuccess = { results ->
            val products = mutableListOf<Product>()
            results.forEach { json ->
                val obj = json.asJsonObject
                val productId = obj.get("id").asInt
                getStockForProduct(productId) { stock ->
                    products.add(Product(
                        id = productId,
                        name = obj.get("name").asString,
                        price = obj.get("list_price").asDouble,
                        imageBase64 = obj.get("image_1920")?.asString,
                        stock = stock,
                        description = obj.get("description_sale")?.asString
                    ))
                    if (products.size == results.size()) {
                        val fragment = ProductListFragment.newInstance(products)
                        supportFragmentManager.beginTransaction()
                            .replace(R.id.fragmentContainer, fragment)
                            .commit()
                        speak("Mostrando ${products.size} productos en $category. Stock real actualizado en Tunja.")
                    }
                }
            }
        }, onError = { error ->
            speak("Error al cargar productos: $error")
            Toast.makeText(this, error, Toast.LENGTH_SHORT).show()
        })
    }

    private fun getStockForProduct(productId: Int, onSuccess: (Int) -> Unit) {
        val domain = listOf(listOf("product_id", "=", productId), listOf("location_id", "=", locationIdTienda))
        OdooHelper.executeOdooRpc(
            "stock.quant",
            "search_read",
            domain,
            fields = mapOf("quantity" to true),
            onSuccess = { results ->
            val stock = if (results.size() > 0) results[0].asJsonObject.get("quantity").asInt else 0
            onSuccess(stock)
        }, onError = { error ->
            Log.e("Odoo", error)
            onSuccess(0)
        })
    }

    private fun scheduleDailyReposition() {
        val jobScheduler = getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
        val component = ComponentName(this, RepositionJobService::class.java)
        val jobInfo = JobInfo.Builder(1, component)
            .setPeriodic(24 * 60 * 60 * 1000)
            .setRequiresCharging(false)
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            .build()
        jobScheduler.schedule(jobInfo)
    }

    private fun triggerRepositionJobNow() {
        val jobScheduler = getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
        val component = ComponentName(this, RepositionJobService::class.java)

        val MANUAL_JOB_ID = 2

        jobScheduler.cancel(MANUAL_JOB_ID)

        val jobInfo = JobInfo.Builder(MANUAL_JOB_ID, component)
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            .setOverrideDeadline(0)
            .build()

        val result = jobScheduler.schedule(jobInfo)
        if (result == JobScheduler.RESULT_SUCCESS) {
            Toast.makeText(this, "Ejecutando reposición ahora...", Toast.LENGTH_SHORT).show()
            jobScheduler.allPendingJobs.forEach { Log.d("Reposition", "Job pendiente id=${it.id} periodic=${it.isPeriodic}") }
        } else {
            Toast.makeText(this, "No se pudo programar la reposición manual.", Toast.LENGTH_LONG).show()
            Log.e("Reposition", "schedule() devolvió RESULT_FAILURE")
        }
    }

    override fun onStart() {
        super.onStart()
        Robot.getInstance().addTtsListener(this)
    }

    override fun onStop() {
        Robot.getInstance().removeTtsListener(this)
        super.onStop()
    }

    override fun onTtsStatusChanged(ttsRequest: TtsRequest) {

    }
}