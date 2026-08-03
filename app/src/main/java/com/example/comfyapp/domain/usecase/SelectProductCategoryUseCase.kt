// coordina el mensaje y el movimiento del robot al seleccionar una categoria
package com.example.comfyapp.domain.usecase

import com.example.comfyapp.domain.model.ProductListRequest
import com.example.comfyapp.domain.repository.RobotRepository

class SelectProductCategoryUseCase(
    private val robotRepository: RobotRepository
) {
    operator fun invoke(request: ProductListRequest, speech: String): ProductListRequest {
        val navigationStarted = robotRepository.goToLocation(request.robotLocation)
        if (navigationStarted) robotRepository.speak(speech)
        return request.copy(showTravelVideo = navigationStarted)
    }
}
