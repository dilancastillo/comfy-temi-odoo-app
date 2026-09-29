// conecta la aplicacion con el broker mqtt y entrega los mensajes recibidos
package com.example.comfyapp.core

import android.util.Log
import org.eclipse.paho.client.mqttv3.*
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence

class MqttController(
    private val onMessage: (String) -> Unit,
    private val onStatus: (String) -> Unit
) {

    @Volatile private var client: MqttClient? = null

    // La conexión TLS con el broker es bloqueante: se hace en un hilo aparte para que la pantalla
    // no se congele (y Android no la cierre por ANR) mientras la red responde.
    fun connect() {
        Thread({ connectBlocking() }, "mqtt-connect").start()
    }

    private fun connectBlocking() {
        try {
            client = MqttClient(
                "ssl://c91d1798a8a74ab68c913b5a83204947.s1.eu.hivemq.cloud:8883",
                "TemiRobot_${System.currentTimeMillis()}",
                MemoryPersistence()
            )

            val options = MqttConnectOptions().apply {
                userName = "andrespa02"
                password = "Comfer1234.".toCharArray()
                isAutomaticReconnect = true
                socketFactory = javax.net.ssl.SSLSocketFactory.getDefault()
            }

            client?.setCallback(object : MqttCallback {
                override fun connectionLost(cause: Throwable?) {
                    onStatus("MQTT desconectado")
                }

                override fun messageArrived(topic: String?, message: MqttMessage?) {
                    message?.toString()?.let {
                        Log.d("MQTT", "Mensaje: $it")
                        onMessage(it)
                    }
                }

                override fun deliveryComplete(token: IMqttDeliveryToken?) {}
            })

            client?.connect(options)
            client?.subscribe("puerta/sensor", 1)

            onStatus("MQTT conectado")

        } catch (e: Exception) {
            Log.e("MQTT", "Error MQTT", e)
            onStatus("Error MQTT")
        }
    }

    fun disconnect() {
        val current = client ?: return
        client = null
        Thread({
            try {
                current.disconnect()
                current.close()
            } catch (_: Exception) {}
        }, "mqtt-disconnect").start()
    }
}
