package com.example.comfyapp

import android.util.Base64
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.comfyapp.databinding.ItemProductBinding

class ProductAdapter(
    private val products: List<Product>,
    private val onItemClick: (Product) -> Unit
) : RecyclerView.Adapter<ProductAdapter.ProductViewHolder>() {

    inner class ProductViewHolder(
        private val binding: ItemProductBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun isValidBase64(data: String): Boolean {
            return try {
                Base64.decode(data, Base64.DEFAULT)
                true
            } catch (e: Exception) {
                false
            }
        }

        fun bind(product: Product) {

            binding.tvName.text = product.name
            binding.tvPrice.text = "Precio: ${product.price}"
            binding.badgeStock.text = "Stock: ${product.stock}"

            val base64 = product.imageBase64

            if (!base64.isNullOrBlank()
                && base64 != "false"
                && base64 != "null"
                && isValidBase64(base64)
            ) {

                val imageData = "data:image/png;base64,$base64"

                Glide.with(binding.root.context)
                    .load(imageData)
                    .into(binding.imgProduct)

            } else {
                // Imagen por defecto
                Glide.with(binding.root.context)
                    .load(R.drawable.ic_erro_load_img) // usa un recurso que sí exista
                    .into(binding.imgProduct)
            }

            binding.root.setOnClickListener { onItemClick(product) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProductViewHolder {
        val binding = ItemProductBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ProductViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ProductViewHolder, position: Int) {
        holder.bind(products[position])
    }

    override fun getItemCount(): Int = products.size
}
