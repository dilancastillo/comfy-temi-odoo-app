package com.example.comfyapp

import android.content.Intent
import android.os.Bundle
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
    }
}