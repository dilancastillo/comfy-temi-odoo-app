// procesa las acciones de categorias y decide la navegacion del flujo principal
package com.example.comfyapp.ui.products.category

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.comfyapp.domain.model.ProductCategory
import com.example.comfyapp.domain.model.ProductListRequest
import com.example.comfyapp.domain.model.ProductVideo
import com.example.comfyapp.domain.repository.RobotRepository
import com.example.comfyapp.domain.usecase.SelectProductCategoryUseCase

class ProductsUserViewModel(
    private val robotRepository: RobotRepository
) : ViewModel() {

    private val selectCategory = SelectProductCategoryUseCase(robotRepository)
    private val _state = MutableLiveData(ProductsUserUiState())
    val state: LiveData<ProductsUserUiState> = _state

    private val _effect = MutableLiveData<ProductsUserEffect?>()
    val effect: LiveData<ProductsUserEffect?> = _effect

    fun startRobot() = robotRepository.start()

    fun announceScreen() = robotRepository.speak(WELCOME_MESSAGE)

    fun stopRobot() = robotRepository.stop()

    fun onAction(action: ProductsUserAction) {
        when (action) {
            ProductsUserAction.SelectFloorAndWall ->
                _effect.value = ProductsUserEffect.OpenTiles

            ProductsUserAction.SelectSanitary -> openProductList(SANITARY_REQUEST)
            ProductsUserAction.SelectTaps -> openProductList(TAPS_REQUEST)
            ProductsUserAction.UserInteraction -> robotRepository.notifyUserInteraction()
            ProductsUserAction.Back -> _effect.value = ProductsUserEffect.Close
        }
    }

    fun effectHandled() {
        _effect.value = null
    }

    private fun openProductList(request: ProductListRequest) {
        _effect.value = ProductsUserEffect.OpenProductList(
            selectCategory(request, "¡Perfecto! Acompáñame.")
        )
    }

    companion object {
        private const val WELCOME_MESSAGE =
            "¡Bienvenido! ¿Qué tipo de producto estás buscando? " +
                "Pisos y paredes, sanitarios o griferías. Toca en mi pantalla y te mostraré " +
                "las últimas tendencias de esta categoría."

        private val SANITARY_REQUEST = ProductListRequest(
            category = ProductCategory.SANITARY,
            title = "Sanitarios y Accesorios",
            firstColumnTitle = "Combos",
            secondColumnTitle = "Solos",
            robotLocation = "sanitarios",
            video = ProductVideo.SANITARY,
            pageSize = 15
        )

        private val TAPS_REQUEST = ProductListRequest(
            category = ProductCategory.TAPS,
            title = "Griferías",
            firstColumnTitle = "Lavamanos",
            secondColumnTitle = "Lavaplatos",
            robotLocation = "griferias",
            video = ProductVideo.TAPS
        )
    }
}
