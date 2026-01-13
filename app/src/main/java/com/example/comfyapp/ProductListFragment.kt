package com.example.comfyapp

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import com.example.comfyapp.databinding.FragmentProductListBinding
import java.io.Serializable

class ProductListFragment : Fragment() {
    private var _binding: FragmentProductListBinding? = null
    private val binding get() = _binding!!

    companion object {
        fun newInstance(products: List<Product>): ProductListFragment {
            return ProductListFragment().apply {
                arguments = Bundle().apply {
                    putSerializable("products", ArrayList(products))
                }
            }
        }
    }


    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentProductListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.recyclerView.layoutManager = GridLayoutManager(context, 3)
        val products = arguments?.getSerializable("products") as? List<Product> ?: emptyList()
        val adapter = ProductAdapter(products) { product ->

            val url = product.website_url
            if (!url.isNullOrBlank()) {

                val finalUrl =
                    if (url.startsWith("https")) url
                    else "https://www.comfer.co$url"

                Log.d("PRODUCT_URL", "URL final: $finalUrl")
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