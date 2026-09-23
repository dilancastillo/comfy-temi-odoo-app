// convierte la seleccion de un ambiente en una configuracion de catalogo
package com.example.comfyapp.ui.products.tiles

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.comfyapp.domain.model.ProductCategory
import com.example.comfyapp.domain.model.ProductListRequest
import com.example.comfyapp.domain.model.ProductVideo
import com.example.comfyapp.domain.repository.RobotRepository
import com.example.comfyapp.domain.usecase.SelectProductCategoryUseCase

class TilesViewModel(
    private val robotRepository: RobotRepository
) : ViewModel() {

    private val selectCategory = SelectProductCategoryUseCase(robotRepository)
    private val _effect = MutableLiveData<TilesEffect?>()
    val effect: LiveData<TilesEffect?> = _effect

    fun startRobot() = robotRepository.start()

    fun cancelNavigationByUser() = robotRepository.cancelNavigationByUser()

    fun announceScreen() {
        robotRepository.speak(
            "¡Súper! ¿Para dónde estás buscando estos productos? Toca la pantalla y acompáñame."
        )
    }

    fun stopRobot() = robotRepository.stop()

    fun onAction(action: TilesAction) {
        when (action) {
            is TilesAction.Select -> {
                val request = requestFor(action.category)
                _effect.value = TilesEffect.OpenProductList(
                    selectCategory(request)
                )
            }
            TilesAction.UserInteraction -> robotRepository.notifyUserInteraction()
            TilesAction.Back -> _effect.value = TilesEffect.Close
        }
    }

    fun effectHandled() {
        _effect.value = null
    }

    private fun requestFor(category: TileCategory): ProductListRequest = when (category) {
        TileCategory.BATHROOMS -> ProductListRequest(
            category = ProductCategory.FLOOR_AND_WALL_BATHROOMS,
            title = "Pisos y paredes para Baños y Cocinas",
            firstColumnTitle = "Únicamente para Paredes",
            secondColumnTitle = "Para Pisos y Paredes",
            robotLocation = "baños revestimientos alfa",
            video = ProductVideo.FLOOR_AND_WALL,
            pageSize = 15,
            maxProductsPerColumn = 15,
            firstColumnOrder = "id desc",
            secondColumnOrder = "id desc"
        )
        TileCategory.SOCIAL_AREAS -> ProductListRequest(
            category = ProductCategory.FLOOR_AND_WALL_SOCIAL,
            title = "Porcelanatos y Ceramicas para Zonas Sociales",
            firstColumnTitle = "Porcelanatos",
            secondColumnTitle = "Ceramica",
            robotLocation = "zona social revestimiento",
            video = ProductVideo.FLOOR_AND_WALL,
            pageSize = 10,
            maxProductsPerColumn = 10,
            firstColumnOrder = "id asc",
            secondColumnOrder = "id desc"
        )
        TileCategory.EXTERIORS -> ProductListRequest(
            category = ProductCategory.FLOOR_AND_WALL_EXTERIORS,
            title = "Pisos y Paredes para Exteriores",
            firstColumnTitle = "Únicamente para Paredes",
            secondColumnTitle = "Para Pisos y Paredes",
            robotLocation = "pisos exteriores alfa",
            video = ProductVideo.FLOOR_AND_WALL,
            pageSize = 15,
            maxProductsPerColumn = 15,
            firstColumnOrder = "id desc",
            secondColumnOrder = "id asc"
        )
    }
}
