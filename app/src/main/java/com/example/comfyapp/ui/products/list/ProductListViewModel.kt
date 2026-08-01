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

    private val _state = MutableLiveData(ProductListUiState())
    val state: LiveData<ProductListUiState> = _state

    private val _effect = MutableLiveData<ProductListEffect?>()
    val effect: LiveData<ProductListEffect?> = _effect

    fun load(request: ProductListRequest) {
        if (_state.value?.isLoaded == true || _state.value?.isLoading == true) return
        _state.value = ProductListUiState(isLoading = true)
        repository.load(
            request = request,
            onSuccess = { first, second ->
                _state.value = ProductListUiState(
                    isLoaded = true,
                    firstColumn = first,
                    secondColumn = second
                )
            },
            onError = { message ->
                _state.value = ProductListUiState()
                _effect.value = ProductListEffect.ShowError(message)
            }
        )
    }

    fun effectHandled() {
        _effect.value = null
    }
}
