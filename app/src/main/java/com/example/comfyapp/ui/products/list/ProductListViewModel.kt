// carga el catalogo y mantiene el estado de productos errores y progreso
package com.example.comfyapp.ui.products.list

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.comfyapp.domain.model.ProductListRequest
import com.example.comfyapp.domain.repository.ProductCatalogRepository

class ProductListViewModel(
    private val repository: ProductCatalogRepository
) : ViewModel() {

    private var currentRequest: ProductListRequest? = null
    private var nextOffset = 0

    private val _state = MutableLiveData(ProductListUiState())
    val state: LiveData<ProductListUiState> = _state

    private val _effect = MutableLiveData<ProductListEffect?>()
    val effect: LiveData<ProductListEffect?> = _effect

    fun load(request: ProductListRequest) {
        if (_state.value?.isLoaded == true || _state.value?.isLoading == true) return
        currentRequest = request
        nextOffset = 0
        loadPage(request, reset = true)
    }

    fun loadNextPage() {
        val request = currentRequest ?: return
        val state = _state.value ?: return
        if (state.isLoading || !state.hasMore) return
        loadPage(request, reset = false)
    }

    fun retry() {
        val request = currentRequest ?: return
        val state = _state.value ?: return
        if (state.isLoading) return
        if (state.isLoaded) {
            loadPage(request, reset = false)
        } else {
            nextOffset = 0
            loadPage(request, reset = true)
        }
    }

    private fun loadPage(request: ProductListRequest, reset: Boolean) {
        val previous = if (reset) ProductListUiState() else requireNotNull(_state.value)
        _state.value = previous.copy(isLoading = true)
        repository.load(
            request = request,
            offset = nextOffset,
            onSuccess = { first, second ->
                val firstColumn = (previous.firstColumn + first).distinctBy { it.id }
                val secondColumn = (previous.secondColumn + second).distinctBy { it.id }
                nextOffset += request.pageSize
                _state.value = ProductListUiState(
                    isLoading = false,
                    isLoaded = true,
                    firstColumn = firstColumn,
                    secondColumn = secondColumn,
                    hasMore = first.size == request.pageSize || second.size == request.pageSize
                )
            },
            onError = { message ->
                _state.value = previous.copy(isLoading = false)
                _effect.value = if (previous.isLoaded) {
                    ProductListEffect.ShowPaginationError
                } else {
                    ProductListEffect.ShowInitialError
                }
            }
        )
    }

    fun effectHandled() {
        _effect.value = null
    }
}
