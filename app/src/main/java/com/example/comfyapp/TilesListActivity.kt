package com.example.comfyapp

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.comfyapp.databinding.ActivityTilesListBinding
import com.robotemi.sdk.Robot
import com.robotemi.sdk.TtsRequest



class TilesListActivity : AppCompatActivity() {
    private lateinit var binding: ActivityTilesListBinding
    private lateinit var robot: Robot
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityTilesListBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.imgbtnback.setOnClickListener {
            finish()
        }
    }
    override fun onResume(){
        super.onResume()
        robot = Robot.getInstance()
        robot.speak(TtsRequest.create("¡Súper!. ¿Para dónde estás buscando estos productos?. Toca mi pantalla, y acompáñame",false))

    }
}