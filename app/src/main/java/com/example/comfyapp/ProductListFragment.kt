package com.example.comfyapp

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import com.example.comfyapp.databinding.FragmentProductListBinding
import java.io.Serializable

class ProductListFragment : Fragment() {
    private var _binding: FragmentProductListBinding? = null
    private val binding get() = _binding!!

    companion object {
        fun newInstance(products: List<Product>): ProductListFragment {
            val fragment = ProductListFragment()
            val args = Bundle().apply {
                putSerializable("products", products as Serializable)
            }
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentProductListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.recyclerView.layoutManager = GridLayoutManager(context, 2)
        val products = arguments?.getSerializable("products") as? List<Product> ?: emptyList()
        val adapter = ProductAdapter(products) { product ->
            (activity as? MainActivity)?.speak("Producto: ${product.name}, Precio: ${product.price}, Inventario en Tunja: ${product.stock}. Descripción: ${product.description}")
        }
        binding.recyclerView.adapter = adapter
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}