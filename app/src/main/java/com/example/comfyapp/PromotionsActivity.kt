package com.example.comfyapp

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.comfyapp.databinding.PromotionsBinding
import com.example.comfyapp.R



class PromotionsActivity : AppCompatActivity() {
    private lateinit var binding: PromotionsBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = PromotionsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val uri = Uri.parse("android.resource://${packageName}/${R.raw.promo}")
        binding.videoPromo.setVideoURI(uri)

        binding.videoPromo.setOnPreparedListener { mp ->
            mp.isLooping = true
            mp.setVolume(0f, 0f)
        }
        binding.videoPromo.start()
        binding.btnGotopromotions.setOnClickListener {
            startActivity(Intent(this, SelectPromoActivity::class.java))
        }
        binding.imgbtnback.setOnClickListener {
            finish()
        }
    }
    override fun onResume(){
        super.onResume()
        binding.videoPromo.start()
    }
}