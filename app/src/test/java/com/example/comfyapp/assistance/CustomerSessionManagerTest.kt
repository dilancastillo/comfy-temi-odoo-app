// verifica que el contexto del cliente se enriquezca turno a turno sin perder lo que ya dijo
package com.example.comfyapp.assistance

import com.example.comfyapp.domain.model.AssistanceType
import com.example.comfyapp.domain.model.IntentAnalysis
import com.example.comfyapp.domain.model.ProductCategory
import com.example.comfyapp.domain.model.ProductType
import com.example.comfyapp.session.CustomerSessionManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CustomerSessionManagerTest {

    @Before
    fun setUp() = CustomerSessionManager.reset()

    @Test
    fun `session memory survives four turns`() {
        CustomerSessionManager.merge(
            IntentAnalysis(AssistanceType.PROJECT_ASSISTANCE, project = "remodelación", space = "baño")
        )
        CustomerSessionManager.merge(
            IntentAnalysis(AssistanceType.CATEGORY_BROWSE, category = ProductCategory.SANITARY)
        )
        CustomerSessionManager.merge(IntentAnalysis(AssistanceType.CATEGORY_BROWSE, style = "moderno"))
        CustomerSessionManager.merge(
            IntentAnalysis(AssistanceType.CATEGORY_BROWSE, budgetLevel = "economico_moderado")
        )

        val context = CustomerSessionManager.current()
        assertEquals("remodelación", context.project)
        assertEquals("baño", context.space)
        assertEquals(ProductCategory.SANITARY, context.category)
        assertEquals("moderno", context.style)
        assertEquals("economico_moderado", context.budgetLevel)
    }

    @Test
    fun `correcting one attribute replaces only that field`() {
        CustomerSessionManager.merge(
            IntentAnalysis(
                AssistanceType.CATEGORY_BROWSE,
                category = ProductCategory.TAPS,
                space = "baño",
                color = "negro"
            )
        )
        CustomerSessionManager.merge(IntentAnalysis(AssistanceType.CLARIFICATION, color = "blanco"))

        val context = CustomerSessionManager.current()
        assertEquals("blanco", context.color)
        assertEquals(ProductCategory.TAPS, context.category)
        assertEquals("baño", context.space)
        // una aclaración no borra el tipo de ayuda que ya se había identificado
        assertEquals(AssistanceType.CATEGORY_BROWSE, context.assistanceType)
    }

    @Test
    fun `max price is kept across turns and replaced when the customer changes it`() {
        CustomerSessionManager.merge(
            IntentAnalysis(AssistanceType.CATEGORY_BROWSE, category = ProductCategory.TAPS, maxPrice = 100000.0)
        )
        CustomerSessionManager.merge(IntentAnalysis(AssistanceType.CATEGORY_BROWSE, color = "negro"))
        assertEquals(100000.0, CustomerSessionManager.current().maxPrice!!, 0.0)

        CustomerSessionManager.merge(IntentAnalysis(AssistanceType.CLARIFICATION, maxPrice = 250000.0))
        assertEquals(250000.0, CustomerSessionManager.current().maxPrice!!, 0.0)
    }

    @Test
    fun `product type is dropped when the category changes`() {
        CustomerSessionManager.merge(
            IntentAnalysis(AssistanceType.CATEGORY_BROWSE, category = ProductCategory.SANITARY, productType = ProductType.COMBO)
        )
        assertEquals(ProductType.COMBO, CustomerSessionManager.current().productType)

        CustomerSessionManager.merge(IntentAnalysis(AssistanceType.CATEGORY_BROWSE, category = ProductCategory.TAPS))
        assertNull(CustomerSessionManager.current().productType)
    }

    @Test
    fun `advisor request keeps project context`() {
        CustomerSessionManager.merge(
            IntentAnalysis(AssistanceType.HUMAN_ADVISOR, project = "remodelación", space = "baño")
        )

        val context = CustomerSessionManager.current()
        assertTrue(context.advisorRequested)
        assertEquals("remodelación", context.project)
    }

    @Test
    fun `changing category drops the previous exact query`() {
        CustomerSessionManager.merge(
            IntentAnalysis(
                AssistanceType.EXACT_PRODUCT,
                category = ProductCategory.SANITARY,
                exactProductQuery = "acuacer"
            )
        )
        CustomerSessionManager.merge(IntentAnalysis(AssistanceType.CATEGORY_BROWSE, category = ProductCategory.TAPS))

        assertNull(CustomerSessionManager.current().exactProductQuery)
    }

    @Test
    fun `technical needs accumulate`() {
        CustomerSessionManager.merge(IntentAnalysis(AssistanceType.TECHNICAL_NEED, technicalNeeds = listOf("antideslizante")))
        CustomerSessionManager.merge(IntentAnalysis(AssistanceType.TECHNICAL_NEED, technicalNeeds = listOf("exterior")))

        assertEquals(setOf("antideslizante", "exterior"), CustomerSessionManager.current().technicalNeeds)
    }

    @Test
    fun `adding a detail keeps the confirmation but changing category requires it again`() {
        CustomerSessionManager.merge(IntentAnalysis(AssistanceType.CATEGORY_BROWSE, category = ProductCategory.SANITARY))
        CustomerSessionManager.markConfirmed()

        val detail = CustomerSessionManager.merge(IntentAnalysis(AssistanceType.CATEGORY_BROWSE, style = "moderno"))
        assertFalse(detail.isSignificantChange)
        assertTrue(CustomerSessionManager.isConfirmed())

        val change = CustomerSessionManager.merge(IntentAnalysis(AssistanceType.CATEGORY_BROWSE, category = ProductCategory.TAPS))
        assertTrue(change.isSignificantChange)
        assertFalse(CustomerSessionManager.isConfirmed())
    }

    @Test
    fun `switching from browsing to only location requires confirmation again`() {
        CustomerSessionManager.merge(IntentAnalysis(AssistanceType.CATEGORY_BROWSE, category = ProductCategory.SANITARY))
        CustomerSessionManager.markConfirmed()

        val result = CustomerSessionManager.merge(IntentAnalysis(AssistanceType.LOCATION_ONLY))

        assertTrue(result.isSignificantChange)
    }

    @Test
    fun `idle session is detected only after the timeout`() {
        CustomerSessionManager.merge(IntentAnalysis(AssistanceType.CATEGORY_BROWSE), nowMs = 1_000L)

        assertFalse(CustomerSessionManager.isIdleFor(60_000L, nowMs = 30_000L))
        assertTrue(CustomerSessionManager.isIdleFor(60_000L, nowMs = 61_000L))
    }

    @Test
    fun `reset clears the customer`() {
        CustomerSessionManager.merge(IntentAnalysis(AssistanceType.CATEGORY_BROWSE, category = ProductCategory.SANITARY))
        CustomerSessionManager.markConfirmed()

        CustomerSessionManager.reset()

        assertFalse(CustomerSessionManager.hasActiveCustomer())
        assertFalse(CustomerSessionManager.isConfirmed())
    }
}
