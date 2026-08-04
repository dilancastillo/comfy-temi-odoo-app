// verifica las decisiones principales de los modelos de vista del catalogo
package com.example.comfyapp.ui.products

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.example.comfyapp.domain.model.CatalogProduct
import com.example.comfyapp.domain.model.ProductCategory
import com.example.comfyapp.domain.model.ProductListRequest
import com.example.comfyapp.domain.model.ProductVideo
import com.example.comfyapp.domain.repository.ProductCatalogRepository
import com.example.comfyapp.domain.repository.RobotRepository
import com.example.comfyapp.ui.products.category.ProductsUserAction
import com.example.comfyapp.ui.products.category.ProductsUserEffect
import com.example.comfyapp.ui.products.category.ProductsUserViewModel
import com.example.comfyapp.ui.products.list.ProductListViewModel
import com.example.comfyapp.ui.products.tiles.TileCategory
import com.example.comfyapp.ui.products.tiles.TilesAction
import com.example.comfyapp.ui.products.tiles.TilesEffect
import com.example.comfyapp.ui.products.tiles.TilesViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ProductsViewModelsTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    @Test
    fun `sanitary selection moves robot and emits typed request`() {
        val robot = FakeRobotRepository()
        val viewModel = ProductsUserViewModel(robot)

        viewModel.onAction(ProductsUserAction.SelectSanitary)

        assertEquals("sanitarios", robot.lastLocation)
        val effect = viewModel.effect.value as ProductsUserEffect.OpenProductList
        assertEquals(ProductCategory.SANITARY, effect.request.category)
        assertEquals("Combos", effect.request.firstColumnTitle)
    }

    @Test
    fun `floor selection opens tiles without moving robot`() {
        val robot = FakeRobotRepository()
        val viewModel = ProductsUserViewModel(robot)

        viewModel.onAction(ProductsUserAction.SelectFloorAndWall)

        assertEquals(ProductsUserEffect.OpenTiles, viewModel.effect.value)
        assertEquals(null, robot.lastLocation)
    }

    @Test
    fun `social area selection produces expected product configuration`() {
        val robot = FakeRobotRepository()
        val viewModel = TilesViewModel(robot)

        viewModel.onAction(TilesAction.Select(TileCategory.SOCIAL_AREAS))

        val effect = viewModel.effect.value as TilesEffect.OpenProductList
        assertEquals(listOf(24, 13, 18), effect.request.usedInIds)
        assertEquals("pisos exteriores alfa", robot.lastLocation)
    }

    @Test
    fun `product list exposes both repository columns`() {
        val first = listOf(product(1))
        val second = listOf(product(2))
        val repository = FakeProductRepository(first, second)
        val viewModel = ProductListViewModel(repository)

        viewModel.load(request())

        val state = requireNotNull(viewModel.state.value)
        assertFalse(state.isLoading)
        assertTrue(state.isLoaded)
        assertEquals(first, state.firstColumn)
        assertEquals(second, state.secondColumn)
    }

    @Test
    fun `product list accumulates pages using the expected offsets`() {
        val repository = FakePagedProductRepository()
        val viewModel = ProductListViewModel(repository)
        val request = request().copy(pageSize = 2)

        viewModel.load(request)
        viewModel.loadNextPage()

        val state = requireNotNull(viewModel.state.value)
        assertEquals(listOf(0, 2), repository.offsets)
        assertEquals(listOf(1, 2, 3), state.firstColumn.map { it.id })
        assertEquals(listOf(11, 12, 13), state.secondColumn.map { it.id })
        assertFalse(state.hasMore)
    }

    private fun request() = ProductListRequest(
        category = ProductCategory.TAPS,
        title = "Griferías",
        firstColumnTitle = "Lavamanos",
        secondColumnTitle = "Lavaplatos",
        robotLocation = "griferias",
        video = ProductVideo.TAPS
    )

    private fun product(id: Int) = CatalogProduct(
        id = id,
        name = "Product $id",
        price = 10.0,
        stock = 2.0,
        imageUrl = null,
        description = null,
        websiteUrl = null
    )
}

private class FakeRobotRepository : RobotRepository {
    var lastLocation: String? = null
    override fun start() = Unit
    override fun stop() = Unit
    override fun speak(message: String) = Unit
    override fun goToLocation(location: String): Boolean {
        lastLocation = location
        return true
    }
    override fun cancelNavigationByUser() = Unit
    override fun cancelNavigationForCatalogError() = Unit
        override fun notifyUserInteraction() = Unit
        override fun isAtCenterSala() = false
}

private class FakeProductRepository(
    private val first: List<CatalogProduct>,
    private val second: List<CatalogProduct>
) : ProductCatalogRepository {
    override fun load(
        request: ProductListRequest,
        offset: Int,
        onSuccess: (List<CatalogProduct>, List<CatalogProduct>) -> Unit,
        onError: (String) -> Unit
    ) = onSuccess(first, second)
}

private class FakePagedProductRepository : ProductCatalogRepository {
    val offsets = mutableListOf<Int>()

    override fun load(
        request: ProductListRequest,
        offset: Int,
        onSuccess: (List<CatalogProduct>, List<CatalogProduct>) -> Unit,
        onError: (String) -> Unit
    ) {
        offsets += offset
        if (offset == 0) {
            onSuccess(listOf(product(1), product(2)), listOf(product(11), product(12)))
        } else {
            onSuccess(listOf(product(3)), listOf(product(13)))
        }
    }

    private fun product(id: Int) = CatalogProduct(
        id = id,
        name = "Product $id",
        price = 10.0,
        stock = 2.0,
        imageUrl = null,
        description = null,
        websiteUrl = null
    )
}
