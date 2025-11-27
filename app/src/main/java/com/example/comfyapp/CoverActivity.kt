package com.example.comfyapp

import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import com.example.comfyapp.databinding.ActivityCoverBinding

class CoverActivity : AppCompatActivity() {
    private lateinit var binding: ActivityCoverBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCoverBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnEmpezar.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
        }
        // Botón que va a NewProductsActivity
        binding.btnNewProducts.setOnClickListener {
            startActivity(Intent(this, NewProductsActivity::class.java))
        }
        val btnBack = findViewById<ImageView>(R.id.imgbexit)
        btnBack.setOnClickListener {
            finishAffinity()   // cierra todas las actividades
            System.exit(0)     // termina proceso (opcional pero hace que salga de una)
        }
    }
}