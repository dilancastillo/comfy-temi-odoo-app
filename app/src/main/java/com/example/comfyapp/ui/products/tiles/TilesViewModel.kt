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
                    selectCategory(request, "¡Excelente! Acompáñame.")
                )
            }
            TilesAction.UserInteraction -> robotRepository.notifyUserInteraction()
            TilesAction.Back -> _effect.value = TilesEffect.Close
        }
    }

    fun effectHandled() {
        _effect.value = null
    }

    private fun requestFor(category: TileCategory): ProductListRequest {
        val (title, usedInIds) = when (category) {
            TileCategory.BATHROOMS -> "Pisos y paredes para Baños y zonas húmedas" to listOf(15)
            TileCategory.KITCHENS -> "Pisos y paredes para Cocinas" to listOf(14)
            TileCategory.SOCIAL_AREAS -> "Pisos y Paredes para Zonas Sociales" to listOf(24, 13, 18)
            TileCategory.EXTERIORS -> "Pisos y Paredes para Exteriores" to listOf(17)
        }
        return ProductListRequest(
            category = ProductCategory.FLOOR_AND_WALL,
            title = title,
            firstColumnTitle = "Únicamente para Paredes",
            secondColumnTitle = "Para Pisos y Paredes",
            robotLocation = "pisos exteriores alfa",
            video = ProductVideo.FLOOR_AND_WALL,
            usedInIds = usedInIds
        )
    }
}
