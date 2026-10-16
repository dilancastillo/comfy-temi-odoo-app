// coordina el mensaje y el movimiento del robot al seleccionar una categoria
package com.example.comfyapp.domain.usecase

import com.example.comfyapp.domain.model.ProductListRequest
import com.example.comfyapp.domain.repository.RobotRepository
import com.example.comfyapp.session.CustomerSessionManager

class SelectProductCategoryUseCase(
    private val robotRepository: RobotRepository
) {
    operator fun invoke(request: ProductListRequest): ProductListRequest {
        // Al llegar, Temi conecta la zona con lo que el cliente ya pidió en esta visita.
        val arrival = NavigationMessages.arrival(
            request.robotLocation, CustomerSessionManager.current().activeNeed, showingProducts = true
        )
        val navigationStarted = robotRepository.goToLocation(request.robotLocation, arrivalMessage = arrival)
        return request.copy(showTravelVideo = navigationStarted)
    }
}
