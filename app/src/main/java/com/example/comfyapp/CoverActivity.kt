// presenta la entrada principal y permite iniciar el flujo de atencion al cliente
package com.example.comfyapp

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.comfyapp.core.MqttController
import com.example.comfyapp.robot.TemiRobotRepository
import com.example.comfyapp.ui.RobotInactivityNavigator
import com.example.comfyapp.ui.products.category.ProductsUserActivity
import com.example.comfyapp.databinding.ActivityCoverBinding
import com.robotemi.sdk.Robot
import com.robotemi.sdk.TtsRequest
import com.robotemi.sdk.permission.Permission

class CoverActivity : AppCompatActivity() {
    private lateinit var binding: ActivityCoverBinding
    private lateinit var mqttController: MqttController
    private lateinit var robotRepository: TemiRobotRepository
    private val inactivityNavigator by lazy { RobotInactivityNavigator(this) }
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
        robotRepository.start()

        binding.btnclient.setOnClickListener {
            startActivity(Intent(this, ProductsUserActivity::class.java))
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
        robotRepository = TemiRobotRepository(
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
            robotRepository.goToLocation(ubicacion)
        }
        if (ubicacion.equals("promococina")){
            robot.speak(TtsRequest.create("¡Ven sígueme y te mostraré las promociones de cocinas!", true))
            robotRepository.goToLocation(ubicacion)
        }
        if (ubicacion.equals("promolavamanos")){
            robot.speak(TtsRequest.create("¡Ven sígueme y te mostraré las promociones de lavamanos!", true))
            robotRepository.goToLocation(ubicacion)
        }
        else{
            robotRepository.goToLocation(ubicacion)
        }

    }

    override fun onDestroy() {
        super.onDestroy()
        // Limpiar recursos
        try {
            mqttController.disconnect()
            robotRepository.stop()
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
        inactivityNavigator.start()
        // Asegurar que Temi no esté en Lock Task Mode
        try {
            if (isTaskRoot) {
                stopLockTask()
            }
        } catch (e: Exception) {
            Log.d(TAG, "No estaba en Lock Task Mode")
        }
    }

    override fun onStop() {
        inactivityNavigator.stop()
        super.onStop()
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        robotRepository.notifyUserInteraction()
    }
}
