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
import androidx.recyclerview.widget.RecyclerView
import com.example.comfyapp.WebViewActivity
import com.example.comfyapp.databinding.FragmentProductListBinding
import com.example.comfyapp.domain.model.Product
import java.io.Serializable

class ProductListFragment : Fragment() {

    private var _binding: FragmentProductListBinding? = null
    private val binding get() = _binding!!

    private var products: List<Product> = emptyList()
    private var onLoadMore: (() -> Unit)? = null
    private var adapter: ProductAdapter? = null

    companion object {
        fun newInstance(): ProductListFragment {
            return ProductListFragment()
        }
    }

    fun setProducts(list: List<Product>) {
        products = list
        adapter?.submitProducts(list)
    }

    fun setOnLoadMore(listener: () -> Unit) {
        onLoadMore = listener
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

        adapter = ProductAdapter(products) { product ->

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
        binding.recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dy > 0 && !recyclerView.canScrollVertically(1)) {
                    onLoadMore?.invoke()
                }
            }
        })
    }

    override fun onDestroyView() {
        adapter = null
        super.onDestroyView()
        _binding = null
    }
}
