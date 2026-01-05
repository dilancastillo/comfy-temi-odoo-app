package com.example.comfyapp

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.comfyapp.databinding.ActivityHomeUserBinding

class HomeUserActivity : AppCompatActivity() {
    private lateinit var binding: ActivityHomeUserBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeUserBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnPromotions.setOnClickListener{
            startActivity(Intent(this, PromotionsActivity::class.java))
        }
        binding.btnproducts.setOnClickListener{
            startActivity(Intent(this, ProductsUserActivity::class.java))
        }
    }
}