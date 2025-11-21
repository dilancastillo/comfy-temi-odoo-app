package com.example.comfyapp

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.comfyapp.databinding.FragmentNewproductsBinding

class NewProductsActivity : AppCompatActivity() {

    private lateinit var binding: FragmentNewproductsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Inflar el layout correcto
        binding = FragmentNewproductsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // ↩️ Botón regresar
        binding.imgbtnback.setOnClickListener {
            finish()
        }



        // 👉 Card de Revestimientos
        binding.btnRevestimientos.setOnClickListener {
            // Abre la pantalla donde estará el FrameLayout (fragmentContainer2)
            val intent = Intent(this, SelectNewProductActivity::class.java)

            // OPCIONAL: Pasar info de qué categoría se tocó
            intent.putExtra("category", 5)

            startActivity(intent)
        }
    }
}
