package com.example.comfyapp

import android.content.Intent
import android.util.Log
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

        fun bind(product: Product) {
            Log.d("ADAPTER_BIND", "Binding producto: ${product.name}")

            binding.tvName.text = product.name
            binding.tvPrice.text = "Precio: ${product.price}"
            binding.badgeStock.text = "Stock: ${product.stock}"

            // CARGA POR URL (NO BASE64)
            Glide.with(binding.imgProduct.context)
                .load(product.imageUrl)
                .placeholder(R.drawable.ic_erro_load_img)
                .error(R.drawable.ic_erro_load_img)
                .centerCrop()
                .into(binding.imgProduct)

            binding.root.setOnClickListener {
                onItemClick(product)
            }
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
