// muestra una columna de productos y abre la pagina web del producto seleccionado
package com.example.comfyapp.ui.products.list

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import com.example.comfyapp.WebViewActivity
import com.example.comfyapp.databinding.FragmentProductListBinding
import com.example.comfyapp.domain.model.Product
import java.io.Serializable

class ProductListFragment : Fragment() {

    private var _binding: FragmentProductListBinding? = null
    private val binding get() = _binding!!

    private var products: List<Product> = emptyList()

    companion object {
        fun newInstance(): ProductListFragment {
            return ProductListFragment()
        }
    }

    fun setProducts(list: List<Product>) {
        products = list
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProductListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.recyclerView.layoutManager = GridLayoutManager(context, 2)

        val adapter = ProductAdapter(products) { product ->

            val url = product.website_url
            if (!url.isNullOrBlank()) {

                val finalUrl =
                    if (url.startsWith("https")) url
                    else "https://www.comfer.co$url"

                val intent = Intent(requireContext(), WebViewActivity::class.java).apply {
                    putExtra(WebViewActivity.EXTRA_URL, finalUrl)
                    putExtra(WebViewActivity.EXTRA_ID, product.id)
                }

                startActivity(intent)

            } else {
                Toast.makeText(
                    requireContext(),
                    "Este producto no tiene página web",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        binding.recyclerView.adapter = adapter
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
