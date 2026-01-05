package com.example.comfyapp

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.comfyapp.core.MqttController
import com.example.comfyapp.core.TemiController
import com.example.comfyapp.databinding.ActivityCoverBinding
import com.robotemi.sdk.Robot
import com.robotemi.sdk.TtsRequest
import com.robotemi.sdk.permission.Permission

class CoverActivity : AppCompatActivity() {
    private lateinit var binding: ActivityCoverBinding
    private lateinit var mqttController: MqttController
    private lateinit var temiController: TemiController
    private val robot: Robot = Robot.getInstance()

    companion object {
        private const val TAG = "CoverActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCoverBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Desactivar Lock Task si está activo
        desactivarLockTask()

        // Solicitar permisos de Temi necesarios
        solicitarPermisosTemi()

        // Inicializar controladores
        inicializarControladores()

        // Conectar MQTT después de un pequeño delay
        binding.root.postDelayed({
            try {
                mqttController.connect()
            } catch (e: Exception) {
                Log.e(TAG, "Error conectando MQTT", e)
                Toast.makeText(this, "Error MQTT: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }, 500)

        // Iniciar Temi
        temiController.start()

        /*binding.btnEmpezar.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
        }*/

        binding.btnNewProducts.setOnClickListener {
            startActivity(Intent(this, NewProductsActivity::class.java))
        }
        binding.btnpromotions.setOnClickListener {
            startActivity(Intent(this, HomeUserActivity::class.java))
        }

        val btnBack = findViewById<ImageView>(R.id.imgbexit)
        btnBack.setOnClickListener {
            finishAffinity()
            System.exit(0)
        }
    }

    private fun desactivarLockTask() {
        try {
            stopLockTask()
            Log.d(TAG, "Lock Task Mode desactivado")
        } catch (e: Exception) {
            Log.d(TAG, "No estaba en Lock Task Mode: ${e.message}")
        }
    }

    private fun solicitarPermisosTemi() {
        // Solicitar permisos necesarios para Temi
        robot.requestPermissions(
            listOf(
                Permission.MAP,
                Permission.SETTINGS,
                Permission.SEQUENCE
            ),
            0
        )
    }

    private fun inicializarControladores() {
        // Configurar MQTT Controller
        mqttController = MqttController(
            onMessage = { mensaje ->
                Log.d(TAG, "Mensaje MQTT recibido: $mensaje")
                runOnUiThread {
                    procesarMensajeMqtt(mensaje)
                }
            },
            onStatus = { estado ->
                Log.i(TAG, "Estado MQTT: $estado")
                runOnUiThread {
                    Toast.makeText(this, estado, Toast.LENGTH_SHORT).show()
                }
            }
        )

        // Configurar Temi Controller
        temiController = TemiController(
            context = this,
            onStatus = { estado ->
                Log.i(TAG, "Estado Temi: $estado")
                runOnUiThread {
                    Toast.makeText(this, estado, Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    private fun procesarMensajeMqtt(mensaje: String) {
        // El mensaje recibido es directamente el nombre de la ubicación
        val ubicacion = mensaje.trim()

        if (ubicacion.isEmpty()) {
            Log.w(TAG, "Mensaje vacío recibido")
            return
        }

        Log.i(TAG, "Ubicación recibida: $ubicacion")
        Toast.makeText(this, "Enviando robot a: $ubicacion", Toast.LENGTH_SHORT).show()

        if (ubicacion.equals("promorevestimientos")){
            robot.speak(TtsRequest.create("¡Ven sígueme y te mostraré las promociones de revestimientos!", true))
            temiController.goToLocation(ubicacion)
        }
        if (ubicacion.equals("promococina")){
            robot.speak(TtsRequest.create("¡Ven sígueme y te mostraré las promociones de cocinas!", true))
            temiController.goToLocation(ubicacion)
        }
        if (ubicacion.equals("promolavamanos")){
            robot.speak(TtsRequest.create("¡Ven sígueme y te mostraré las promociones de lavamanos!", true))
            temiController.goToLocation(ubicacion)
        }
        else{
            temiController.goToLocation(ubicacion)
        }

    }

    override fun onDestroy() {
        super.onDestroy()
        // Limpiar recursos
        try {
            mqttController.disconnect()
            temiController.stop()
            Log.i(TAG, "Controladores desconectados")
        } catch (e: Exception) {
            Log.e(TAG, "Error al desconectar", e)
        }
    }

    override fun onResume() {
        super.onResume()
        // Ocultar barra superior de Temi
        try {
            robot.hideTopBar()
        } catch (e: Exception) {
            Log.e(TAG, "No se pudo ocultar TopBar", e)
        }
    }

    override fun onStart() {
        super.onStart()
        // Asegurar que Temi no esté en Lock Task Mode
        try {
            if (isTaskRoot) {
                stopLockTask()
            }
        } catch (e: Exception) {
            Log.d(TAG, "No estaba en Lock Task Mode")
        }
    }
}