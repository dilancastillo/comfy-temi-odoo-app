package com.example.comfyapp

import android.app.job.JobParameters
import android.app.job.JobService
import android.service.chooser.AdditionalContentContract.MethodNames
import android.util.Log
import com.google.gson.JsonArray
import com.robotemi.sdk.Robot
import com.robotemi.sdk.TtsRequest

class RepositionJobService : JobService() {
    override fun onStartJob(params: JobParameters?): Boolean {

        Log.i("Reposition", "onStartJob() llamado. Params=$params")
        Robot.getInstance().speak(TtsRequest.create("Iniciando reposición.", false))

        val yesterday = "2025-09-02"
        /*val yesterday = try {
            java.time.LocalDate.now().minusDays(1).toString()
        } catch (_: Throwable) {
            val cal = java.util.Calendar.getInstance().apply { add(java.util.Calendar.DAY_OF_YEAR, -1) }
            java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(cal.time)
        }*/

        fun m2oName(row: com.google.gson.JsonObject, key: String): String? {
            val v = row.get(key) ?: return null
            return when {
                v.isJsonArray && v.asJsonArray.size() >= 2 -> v.asJsonArray[1].asString
                v.isJsonPrimitive -> v.asString
                else -> null
            }
        }

        fun speakList(title: String, names: List<String>, maxItems: Int = 5) {
            if (names.isEmpty()) {
                Robot.getInstance().speak(TtsRequest.create("$title: ninguno.", false))
                return
            }
            val (show, rest) =
                if (names.size > maxItems) names.take(maxItems) to (names.size - maxItems)
                else names to 0
            val tail = if ( rest > 0) " y $rest más" else ""
            val text = "$title: ${show.joinToString(", ")}$tail."
            Robot.getInstance().speak(TtsRequest.create(text, false))
        }

        val domainEnded = listOf(
            listOf("quantity", "=", 0),
            listOf("write_date", ">=", yesterday)
        )

        OdooHelper.executeOdooRpc(
            "stock.quant",
            "search_read",
            domain = domainEnded,
            fields = mapOf("product_id" to true),
            onSuccess = { ended ->

                val endedNames = mutableSetOf<String>()
                ended.forEach { el ->
                    val row = el.asJsonObject
                    m2oName(row, "product_id")?.let { endedNames.add(it) }
                }

                val domainArrived = listOf(
                    listOf("state", "=", "done"),
                    listOf("date", ">=", yesterday)
                )

                OdooHelper.executeOdooRpc(
                    "stock.move",
                    "search_read",
                    domain = domainArrived,
                    fields = mapOf("product_id" to true),
                    onSuccess = { arrived ->

                        val arrivedMap = linkedMapOf<String, Double>()
                        arrived.forEach { el ->
                            val row = el.asJsonObject
                            val name = m2oName(row, "product_id") ?: return@forEach
                            arrivedMap[name] = (arrivedMap[name] ?: 0.0)
                        }

                        val arrivedList = arrivedMap.entries
                            .sortedByDescending { it.value }
                            .map { "${it.key} (${it.value.toInt()})"}

                        val domainPriceChange = listOf(
                            listOf("write_date", ">=", yesterday)
                        )

                    OdooHelper.executeOdooRpc(
                        "product.product",
                        "search_read",
                        domain = domainPriceChange,
                        fields = mapOf("name" to true, "list_price" to true),
                        onSuccess = { changes ->
                            val nf = java.text.NumberFormat.getCurrencyInstance(java.util.Locale("es", "co"))
                            val changeList = mutableListOf<String>()
                            changes.forEach { el ->
                                val row = el.asJsonObject
                                val name = row.get("name")?.asString ?: return@forEach
                                val price = row.get("list_price")?.asDouble ?: 0.0
                                changeList.add("$name (${nf.format(price)})")
                            }

                            Robot.getInstance().speak(TtsRequest.create("Resumen de reposición", false))
                            speakList("Acabados para quitar", endedNames.toList())
                            speakList("Llegados para exhibir", arrivedList)
                            speakList("Con cambio de precio", changeList)

                            jobFinished(params, false)
                        },
                        onError = { e ->
                            Log.e("Reposition", e)
                            jobFinished(params, false)
                        })
                    },
                    onError = { e ->
                        Log.e("Reposition", e)
                        jobFinished(params, false)
                    })
                },
                onError = { e ->
                    Log.e("Reposition", e)
                    jobFinished(params, false)
                })
                return true
            }

    override fun onStopJob(params: JobParameters?): Boolean {
        return false
    }
}