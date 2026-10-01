// verifica que cada tipo de ayuda termine en la accion esperada y que falten datos se pregunten
package com.example.comfyapp.assistance

import com.example.comfyapp.domain.model.AssistanceAction
import com.example.comfyapp.domain.model.AssistanceType
import com.example.comfyapp.domain.model.CatalogDestination
import com.example.comfyapp.domain.model.CustomerNeed
import com.example.comfyapp.domain.model.ProductCategory
import com.example.comfyapp.domain.usecase.RouteAssistanceUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteAssistanceUseCaseTest {

    private val router = RouteAssistanceUseCase()

    // La frase del cliente repite el tipo de la necesidad salvo en los casos que se prueban aparte.
    private fun route(need: CustomerNeed, nextQuestion: String? = null) =
        router(need, need.assistanceType ?: AssistanceType.CLARIFICATION, nextQuestion)

    @Test
    fun `exact product searches with its query`() {
        val action = route(
            CustomerNeed(
                id = "n",
                assistanceType = AssistanceType.EXACT_PRODUCT,
                category = ProductCategory.SANITARY,
                exactProductQuery = "acuacer"
            )
        )

        assertEquals(AssistanceAction.SearchExactProduct("acuacer", CatalogDestination.SANITARY), action)
    }

    @Test
    fun `a named product is searched even if gemini called it a category`() {
        val action = route(
            CustomerNeed(
                id = "n",
                assistanceType = AssistanceType.CATEGORY_BROWSE,
                category = ProductCategory.TAPS,
                exactProductQuery = "coral",
                budgetLevel = "economico"
            )
        )

        assertEquals(AssistanceAction.SearchExactProduct("coral", CatalogDestination.TAPS), action)
    }

    @Test
    fun `exact product without query asks for it`() {
        val action = route(CustomerNeed(id = "n", assistanceType = AssistanceType.EXACT_PRODUCT))

        assertEquals(AssistanceAction.AskClarification(RouteAssistanceUseCase.ASK_PRODUCT_NAME), action)
    }

    @Test
    fun `category browse opens the category`() {
        val action = route(
            CustomerNeed(id = "n", assistanceType = AssistanceType.CATEGORY_BROWSE, category = ProductCategory.TAPS)
        )

        assertEquals(AssistanceAction.StartCategoryFlow(CatalogDestination.TAPS), action)
    }

    @Test
    fun `category browse without category asks`() {
        val action = route(CustomerNeed(id = "n", assistanceType = AssistanceType.CATEGORY_BROWSE))

        assertTrue(action is AssistanceAction.AskClarification)
    }

    @Test
    fun `category browse with only a space asks the category for that space`() {
        val action = route(CustomerNeed(id = "n", assistanceType = AssistanceType.CATEGORY_BROWSE, space = "cocina"))

        val question = (action as AssistanceAction.AskClarification).question
        assertTrue(question.contains("para cocina"))
    }

    @Test
    fun `floors without space open the space menu`() {
        val action = route(
            CustomerNeed(id = "n", assistanceType = AssistanceType.CATEGORY_BROWSE, category = ProductCategory.FLOOR_AND_WALL)
        )

        assertEquals(AssistanceAction.StartCategoryFlow(CatalogDestination.FLOOR_ANY), action)
    }

    @Test
    fun `project without category keeps talking about the space`() {
        val action = route(
            CustomerNeed(
                id = "n",
                assistanceType = AssistanceType.PROJECT_ASSISTANCE,
                space = "baño"
            )
        )

        val question = (action as AssistanceAction.AskClarification).question
        assertTrue(question.contains("para baño"))
    }

    @Test
    fun `technical need with space goes straight to the right floor zone`() {
        val action = route(
            CustomerNeed(
                id = "n",
                assistanceType = AssistanceType.TECHNICAL_NEED,
                category = ProductCategory.FLOOR_AND_WALL,
                space = "terraza",
                technicalNeeds = setOf("antideslizante", "exterior")
            )
        )

        assertEquals(AssistanceAction.StartCategoryFlow(CatalogDestination.FLOOR_EXTERIORS), action)
    }

    @Test
    fun `technical floor need without space asks only for the space`() {
        val action = route(
            CustomerNeed(
                id = "n",
                assistanceType = AssistanceType.TECHNICAL_NEED,
                category = ProductCategory.FLOOR_AND_WALL,
                technicalNeeds = setOf("antideslizante")
            )
        )

        assertEquals(AssistanceAction.AskClarification(RouteAssistanceUseCase.ASK_FLOOR_SPACE), action)
    }

    @Test
    fun `porcelain for the living room goes to social zone`() {
        val action = route(
            CustomerNeed(
                id = "n",
                assistanceType = AssistanceType.TECHNICAL_NEED,
                category = ProductCategory.FLOOR_AND_WALL,
                space = "sala",
                color = "gris"
            )
        )

        assertEquals(AssistanceAction.StartCategoryFlow(CatalogDestination.FLOOR_SOCIAL), action)
    }

    @Test
    fun `location only navigates without catalog`() {
        val action = route(
            CustomerNeed(id = "n", assistanceType = AssistanceType.LOCATION_ONLY, category = ProductCategory.TAPS)
        )

        assertEquals(AssistanceAction.NavigateToCategory(CatalogDestination.TAPS), action)
    }

    @Test
    fun `location for an exact product uses its category`() {
        val action = route(
            CustomerNeed(
                id = "n",
                assistanceType = AssistanceType.LOCATION_ONLY,
                category = ProductCategory.SANITARY,
                exactProductQuery = "acuacer"
            )
        )

        assertEquals(AssistanceAction.NavigateToCategory(CatalogDestination.SANITARY), action)
    }

    @Test
    fun `location for floors without space asks which zone`() {
        val action = route(
            CustomerNeed(id = "n", assistanceType = AssistanceType.LOCATION_ONLY, category = ProductCategory.FLOOR_AND_WALL)
        )

        assertEquals(AssistanceAction.AskClarification(RouteAssistanceUseCase.ASK_WHICH_FLOOR_ZONE), action)
    }

    @Test
    fun `location without destination asks`() {
        val action = route(CustomerNeed(id = "n", assistanceType = AssistanceType.LOCATION_ONLY))

        assertEquals(AssistanceAction.AskClarification(RouteAssistanceUseCase.ASK_WHICH_ZONE), action)
    }

    @Test
    fun `advisor wins even without category`() {
        val action = route(
            CustomerNeed(id = "n", assistanceType = AssistanceType.HUMAN_ADVISOR)
        )

        assertEquals(AssistanceAction.RequestAdvisor, action)
    }

    @Test
    fun `clarification uses the suggested question`() {
        val action = route(
            CustomerNeed(id = "n", assistanceType = AssistanceType.CLARIFICATION),
            nextQuestion = "¿Qué espacio quieres renovar?"
        )

        assertEquals(AssistanceAction.AskClarification("¿Qué espacio quieres renovar?"), action)
    }

    @Test
    fun `advisor in this phrase wins over a saved need`() {
        val saved = CustomerNeed(id = "n", assistanceType = AssistanceType.CATEGORY_BROWSE, category = ProductCategory.TAPS)

        assertEquals(AssistanceAction.RequestAdvisor, router(saved, AssistanceType.HUMAN_ADVISOR))
    }

    @Test
    fun `just browsing and unsupported have their own exits`() {
        assertEquals(
            AssistanceAction.JustBrowsing,
            route(CustomerNeed(id = "n", assistanceType = AssistanceType.JUST_BROWSING))
        )
        assertEquals(
            AssistanceAction.Unsupported,
            route(CustomerNeed(id = "n", assistanceType = AssistanceType.UNSUPPORTED))
        )
        assertEquals(
            AssistanceAction.AskClarification(RouteAssistanceUseCase.DEFAULT_CLARIFICATION),
            router(null, AssistanceType.CLARIFICATION)
        )
    }

    @Test
    fun `spaces map to the right floor zone`() {
        fun zone(space: String) = CatalogDestination.from(ProductCategory.FLOOR_AND_WALL, space)

        assertEquals(CatalogDestination.FLOOR_BATHROOMS, zone("baño pequeño"))
        assertEquals(CatalogDestination.FLOOR_BATHROOMS, zone("Cocina"))
        assertEquals(CatalogDestination.FLOOR_SOCIAL, zone("comedor"))
        assertEquals(CatalogDestination.FLOOR_EXTERIORS, zone("patio"))
        assertEquals(CatalogDestination.FLOOR_EXTERIORS, zone("Jardín"))
        assertEquals(CatalogDestination.FLOOR_ANY, zone("la casa"))
    }
}
