// verifica que la respuesta de gemini se valide antes de usarla
package com.example.comfyapp.assistance

import com.example.comfyapp.agent.IntentAnalysisParser
import com.example.comfyapp.domain.model.AssistanceType
import com.example.comfyapp.domain.model.CustomerContext
import com.example.comfyapp.domain.model.CustomerNeed
import com.example.comfyapp.domain.model.NeedField
import com.example.comfyapp.domain.model.NeedOperation
import com.example.comfyapp.domain.model.PendingNeedChange
import com.example.comfyapp.domain.repository.AnalysisRequest
import com.example.comfyapp.domain.model.ProductCategory
import com.example.comfyapp.domain.model.ProductType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IntentAnalysisParserTest {

    @Test
    fun `parses the documented example`() {
        val analysis = IntentAnalysisParser.parse(
            """
            {"assistance_type":"PROJECT_ASSISTANCE","category":"SANITARY","exact_product_query":null,
             "space":"baño pequeño","project":"remodelación","style":"moderno",
             "budget_level":"economico_moderado","technical_needs":[],"needs_clarification":false,
             "next_question":null}
            """.trimIndent()
        )

        assertEquals(AssistanceType.PROJECT_ASSISTANCE, analysis.assistanceType)
        assertEquals(ProductCategory.SANITARY, analysis.category)
        assertEquals("baño pequeño", analysis.space)
        assertEquals("economico_moderado", analysis.budgetLevel)
        assertNull(analysis.exactProductQuery)
        assertFalse(analysis.needsClarification)
    }

    @Test
    fun `accepts code fences and missing optional fields`() {
        val analysis = IntentAnalysisParser.parse(
            "```json\n{\"assistance_type\":\"technical_need\",\"technical_needs\":[\"antideslizante\",\" \"]}\n```"
        )

        assertEquals(AssistanceType.TECHNICAL_NEED, analysis.assistanceType)
        assertEquals(listOf("antideslizante"), analysis.technicalNeeds)
    }

    @Test
    fun `unknown category and budget are ignored`() {
        val analysis = IntentAnalysisParser.parse(
            """{"assistance_type":"CATEGORY_BROWSE","category":"PAINT","budget_level":"barato","space":"null"}"""
        )

        assertNull(analysis.category)
        assertNull(analysis.budgetLevel)
        assertNull(analysis.space)
    }

    @Test
    fun `max price is read as a positive number`() {
        val analysis = IntentAnalysisParser.parse(
            """{"assistance_type":"CATEGORY_BROWSE","category":"TAPS","max_price":100000}"""
        )
        assertEquals(100000.0, analysis.maxPrice!!, 0.0)

        val invalid = IntentAnalysisParser.parse("""{"assistance_type":"CATEGORY_BROWSE","max_price":"barato"}""")
        assertNull(invalid.maxPrice)
    }

    @Test
    fun `product type is parsed and unknown values ignored`() {
        val combo = IntentAnalysisParser.parse("""{"assistance_type":"CATEGORY_BROWSE","product_type":"COMBO"}""")
        assertEquals(ProductType.COMBO, combo.productType)

        val unknown = IntentAnalysisParser.parse("""{"assistance_type":"CATEGORY_BROWSE","product_type":"DUCHA"}""")
        assertNull(unknown.productType)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `invalid assistance type is rejected`() {
        IntentAnalysisParser.parse("""{"assistance_type":"SCREEN_SANITARIOS"}""")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `non json answer is rejected`() {
        IntentAnalysisParser.parse("No le entendí")
    }

    @Test
    fun `memory sends accepted needs apart from the pending draft`() {
        val sanitary = CustomerNeed(
            id = "need_01", assistanceType = AssistanceType.CATEGORY_BROWSE,
            category = ProductCategory.SANITARY, space = "baño", technicalNeeds = setOf("ahorrador")
        )
        val taps = CustomerNeed(id = "need_02", category = ProductCategory.TAPS, space = "baño")
        val json = IntentAnalysisParser.memoryToJson(
            AnalysisRequest(
                text = "mejor blanca",
                accepted = CustomerContext(needs = listOf(sanitary), activeNeedId = "need_01"),
                draft = PendingNeedChange(
                    operation = NeedOperation.ADD_NEED,
                    candidate = CustomerContext(needs = listOf(sanitary, taps), activeNeedId = "need_02"),
                    need = taps,
                    accepted = null,
                    changedFields = setOf("category")
                ),
                correcting = true,
                lastQuestion = null
            )
        )

        assertTrue(json.contains("\"mode\":\"CORRECTING\""))
        assertTrue(json.contains("\"active_need\":{\"id\":\"need_01\""))
        assertTrue(json.contains("\"pending_draft\":{\"id\":\"need_02\""))
    }

    @Test
    fun `memory operation and clear fields are parsed`() {
        val analysis = IntentAnalysisParser.parse(
            """{"assistance_type":"CATEGORY_BROWSE","need_operation":"UPDATE_ACTIVE_NEED",
               "clear_fields":["max_price","volumen"],"technical_needs_remove":["ahorrador"]}"""
        )

        assertEquals(NeedOperation.UPDATE_ACTIVE_NEED, analysis.needOperation)
        assertEquals(setOf(NeedField.MAX_PRICE), analysis.clearFields)
        assertEquals(listOf("ahorrador"), analysis.technicalNeedsRemove)
    }
}
