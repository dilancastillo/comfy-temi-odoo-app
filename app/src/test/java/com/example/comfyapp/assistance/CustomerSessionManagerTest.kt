// verifica que cada necesidad del cliente guarde sus propios datos y que lo no confirmado no entre a la memoria
package com.example.comfyapp.assistance

import com.example.comfyapp.domain.model.AssistanceType
import com.example.comfyapp.domain.model.IntentAnalysis
import com.example.comfyapp.domain.model.NeedField
import com.example.comfyapp.domain.model.NeedOperation
import com.example.comfyapp.domain.model.ProductCategory
import com.example.comfyapp.domain.model.ProductType
import com.example.comfyapp.domain.model.RequestedNeed
import com.example.comfyapp.session.CustomerSessionManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CustomerSessionManagerTest {

    private val session = CustomerSessionManager

    @Before
    fun setUp() = session.reset()

    // simula que el cliente confirmó lo que Temi entendió
    private fun accept(analysis: IntentAnalysis, correcting: Boolean = false) {
        session.prepareChange(analysis, correcting)
        session.commitPending(markConfirmed = true)
    }

    @Test
    fun `case 1 - a second product becomes a separate active need`() {
        accept(IntentAnalysis(AssistanceType.CATEGORY_BROWSE, category = ProductCategory.SANITARY, space = "baño",
            technicalNeeds = listOf("ahorrador")))
        val change = session.prepareChange(
            IntentAnalysis(AssistanceType.CATEGORY_BROWSE, category = ProductCategory.TAPS, space = "baño")
        )

        assertEquals(NeedOperation.ADD_NEED, change.operation)
        val taps = change.need!!
        assertEquals(ProductCategory.TAPS, taps.category)
        assertTrue(taps.technicalNeeds.isEmpty())
        session.commitPending(markConfirmed = true)

        val context = session.current()
        assertEquals(2, context.needs.size)
        assertEquals(taps.id, context.activeNeedId)
        val sanitary = context.needs.first { it.category == ProductCategory.SANITARY }
        assertEquals(setOf("ahorrador"), sanitary.technicalNeeds)
    }

    @Test
    fun `case 2 - a new product does not inherit space or technical needs`() {
        accept(IntentAnalysis(AssistanceType.TECHNICAL_NEED, category = ProductCategory.FLOOR_AND_WALL,
            space = "patio", technicalNeeds = listOf("antideslizante")))
        val sanitary = session.prepareChange(
            IntentAnalysis(AssistanceType.CATEGORY_BROWSE, category = ProductCategory.SANITARY)
        ).need!!

        assertNull(sanitary.space)
        assertTrue(sanitary.technicalNeeds.isEmpty())
    }

    @Test
    fun `case 3 - a price limit stays with its own need`() {
        accept(IntentAnalysis(AssistanceType.CATEGORY_BROWSE, category = ProductCategory.SANITARY, maxPrice = 500000.0))
        val taps = session.prepareChange(
            IntentAnalysis(AssistanceType.CATEGORY_BROWSE, category = ProductCategory.TAPS)
        ).need!!

        assertNull(taps.maxPrice)
    }

    @Test
    fun `case 4 - clearing the price limit removes it`() {
        accept(IntentAnalysis(AssistanceType.CATEGORY_BROWSE, category = ProductCategory.TAPS, maxPrice = 100000.0))
        accept(IntentAnalysis(AssistanceType.CATEGORY_BROWSE, clearFields = setOf(NeedField.MAX_PRICE)))

        val taps = session.current().activeNeed!!
        assertNull(taps.maxPrice)
        assertEquals(ProductCategory.TAPS, taps.category)
    }

    @Test
    fun `case 5 - a technical need can be removed without touching the rest`() {
        accept(IntentAnalysis(AssistanceType.CATEGORY_BROWSE, category = ProductCategory.SANITARY, space = "baño",
            technicalNeeds = listOf("ahorrador")))
        accept(IntentAnalysis(AssistanceType.CATEGORY_BROWSE, technicalNeedsRemove = listOf("Ahorrador")))

        val sanitary = session.current().activeNeed!!
        assertTrue(sanitary.technicalNeeds.isEmpty())
        assertEquals(ProductCategory.SANITARY, sanitary.category)
        assertEquals("baño", sanitary.space)
    }

    @Test
    fun `case 6 - correcting the color keeps the valid category and space`() {
        session.prepareChange(IntentAnalysis(AssistanceType.CATEGORY_BROWSE, category = ProductCategory.TAPS,
            space = "baño", color = "negro"))

        val corrected = session.prepareChange(
            IntentAnalysis(AssistanceType.CATEGORY_BROWSE, color = "blanco"), correcting = true
        )

        assertEquals(NeedOperation.CORRECT_PENDING_NEED, corrected.operation)
        assertEquals("blanco", corrected.need!!.color)
        assertEquals(ProductCategory.TAPS, corrected.need!!.category)
        assertEquals("baño", corrected.need!!.space)
    }

    @Test
    fun `case 7 - correcting the product drops the attributes of the wrong one`() {
        session.prepareChange(IntentAnalysis(AssistanceType.CATEGORY_BROWSE, category = ProductCategory.SANITARY,
            space = "baño", productType = ProductType.COMBO, technicalNeeds = listOf("ahorrador")))

        val corrected = session.prepareChange(
            IntentAnalysis(AssistanceType.CATEGORY_BROWSE, category = ProductCategory.TAPS), correcting = true
        ).need!!

        assertEquals(ProductCategory.TAPS, corrected.category)
        assertNull(corrected.productType)
        assertTrue(corrected.technicalNeeds.isEmpty())
        assertEquals("baño", corrected.space)
        // nada de esto llegó todavía a la memoria aceptada
        assertTrue(session.current().needs.isEmpty())
    }

    @Test
    fun `case 8 - another need of the same category gets its own id`() {
        accept(IntentAnalysis(AssistanceType.CATEGORY_BROWSE, category = ProductCategory.SANITARY, space = "baño principal"))
        val change = session.prepareChange(
            IntentAnalysis(AssistanceType.CATEGORY_BROWSE, needOperation = NeedOperation.ADD_NEED, space = "baño de visitas")
        )

        assertEquals(NeedOperation.ADD_NEED, change.operation)
        // "otro para el baño de visitas" toma la categoría de la activa
        assertEquals(ProductCategory.SANITARY, change.need!!.category)
        session.commitPending(markConfirmed = true)
        val sanitaries = session.current().needs.filter { it.category == ProductCategory.SANITARY }
        assertEquals(2, sanitaries.size)
        assertEquals(setOf("baño principal", "baño de visitas"), sanitaries.map { it.space }.toSet())
    }

    @Test
    fun `case 9 - resuming a saved need brings back its original requirements`() {
        accept(IntentAnalysis(AssistanceType.CATEGORY_BROWSE, category = ProductCategory.SANITARY,
            technicalNeeds = listOf("ahorrador"), maxPrice = 500000.0))
        val sanitaryId = session.current().activeNeedId
        accept(IntentAnalysis(AssistanceType.CATEGORY_BROWSE, category = ProductCategory.TAPS))

        val change = session.prepareChange(
            IntentAnalysis(AssistanceType.CATEGORY_BROWSE, needOperation = NeedOperation.RESUME_NEED, category = ProductCategory.SANITARY)
        )

        assertEquals(NeedOperation.RESUME_NEED, change.operation)
        assertEquals(sanitaryId, change.candidate.activeNeedId)
        assertEquals(setOf("ahorrador"), change.need!!.technicalNeeds)
        assertEquals(500000.0, change.need!!.maxPrice!!, 0.0)
        // retomar algo ya confirmado y sin cambios no pide confirmar de nuevo
        assertFalse(change.requiresConfirmation)
    }

    @Test
    fun `naming the category of a saved need modifies that need instead of creating another`() {
        accept(IntentAnalysis(AssistanceType.CATEGORY_BROWSE, category = ProductCategory.SANITARY))
        accept(IntentAnalysis(AssistanceType.CATEGORY_BROWSE, category = ProductCategory.TAPS))

        val change = session.prepareChange(
            IntentAnalysis(AssistanceType.CATEGORY_BROWSE, category = ProductCategory.SANITARY, color = "blanco")
        )

        assertEquals(NeedOperation.RESUME_NEED, change.operation)
        assertEquals("blanco", change.need!!.color)
        session.commitPending(markConfirmed = true)
        assertEquals(2, session.current().needs.size)
        assertNull(session.current().needs.first { it.category == ProductCategory.TAPS }.color)
    }

    @Test
    fun `an unclear reference to two saved needs asks which one`() {
        accept(IntentAnalysis(AssistanceType.CATEGORY_BROWSE, category = ProductCategory.SANITARY, space = "baño principal"))
        accept(IntentAnalysis(AssistanceType.CATEGORY_BROWSE, needOperation = NeedOperation.ADD_NEED, space = "baño de visitas"))
        accept(IntentAnalysis(AssistanceType.CATEGORY_BROWSE, category = ProductCategory.TAPS))
        val before = session.current()

        val change = session.prepareChange(
            IntentAnalysis(AssistanceType.CATEGORY_BROWSE, needOperation = NeedOperation.RESUME_NEED, category = ProductCategory.SANITARY)
        )

        assertEquals(2, change.ambiguousNeeds.size)
        assertEquals(before, session.current())

        // la respuesta "el de visitas" llega con el id que eligió Gemini
        val visitsId = before.needs.first { it.space == "baño de visitas" }.id
        val resolved = session.prepareChange(
            IntentAnalysis(AssistanceType.CATEGORY_BROWSE, needOperation = NeedOperation.RESUME_NEED, targetNeedId = visitsId)
        )
        assertEquals(visitsId, resolved.candidate.activeNeedId)
    }

    @Test
    fun `a reference to a need that does not exist changes nothing`() {
        accept(IntentAnalysis(AssistanceType.CATEGORY_BROWSE, category = ProductCategory.TAPS))
        val before = session.current()

        val byCategory = session.prepareChange(
            IntentAnalysis(AssistanceType.CATEGORY_BROWSE, needOperation = NeedOperation.RESUME_NEED, category = ProductCategory.SANITARY)
        )
        val byId = session.prepareChange(
            IntentAnalysis(AssistanceType.CATEGORY_BROWSE, needOperation = NeedOperation.RESUME_NEED,
                targetNeedId = "need_99", category = ProductCategory.SANITARY)
        )

        assertTrue(byCategory.resumeNotFound)
        assertTrue(byId.resumeNotFound)
        assertEquals(before, session.current())
    }

    @Test
    fun `case 12 - two products in one phrase become two separate needs`() {
        val change = session.prepareChange(
            IntentAnalysis(
                AssistanceType.CATEGORY_BROWSE, category = ProductCategory.SANITARY, space = "baño",
                technicalNeeds = listOf("ahorrador"),
                additionalNeeds = listOf(RequestedNeed(category = ProductCategory.TAPS, space = "baño", color = "negro"))
            )
        )

        assertEquals(ProductCategory.SANITARY, change.need!!.category)
        assertEquals(1, change.additionalNeeds.size)
        val taps = change.additionalNeeds.single()
        assertEquals("negro", taps.color)
        assertTrue(taps.technicalNeeds.isEmpty())
        assertNull(change.need!!.color)

        session.commitPending(markConfirmed = true)
        val context = session.current()
        assertEquals(2, context.needs.size)
        assertEquals(change.need!!.id, context.activeNeedId)
        // la grifería queda anotada pero sin confirmar: se confirma cuando se retome
        assertFalse(context.need(taps.id)!!.isConfirmed)
    }

    @Test
    fun `case 10 - a discarded draft leaves the accepted memory untouched`() {
        accept(IntentAnalysis(AssistanceType.CATEGORY_BROWSE, category = ProductCategory.SANITARY))
        val before = session.current()

        session.prepareChange(IntentAnalysis(AssistanceType.CATEGORY_BROWSE, category = ProductCategory.TAPS))
        session.discardPending()

        assertEquals(before, session.current())
        assertNull(session.pending())
    }

    @Test
    fun `clarification answers keep filling the same draft`() {
        session.prepareChange(IntentAnalysis(AssistanceType.PROJECT_ASSISTANCE, project = "remodelación", space = "baño"))
        val change = session.prepareChange(IntentAnalysis(AssistanceType.CATEGORY_BROWSE, category = ProductCategory.SANITARY))

        assertEquals(NeedOperation.UPDATE_ACTIVE_NEED, change.operation)
        assertEquals("baño", change.need!!.space)
        assertEquals("remodelación", change.candidate.project)
    }

    @Test
    fun `session memory survives four turns on the same need`() {
        accept(IntentAnalysis(AssistanceType.PROJECT_ASSISTANCE, project = "remodelación", space = "baño"))
        accept(IntentAnalysis(AssistanceType.CATEGORY_BROWSE, category = ProductCategory.SANITARY))
        accept(IntentAnalysis(AssistanceType.CATEGORY_BROWSE, style = "moderno"))
        accept(IntentAnalysis(AssistanceType.CATEGORY_BROWSE, budgetLevel = "economico_moderado"))

        val context = session.current()
        val need = context.activeNeed!!
        assertEquals(1, context.needs.size)
        assertEquals("remodelación", context.project)
        assertEquals("baño", need.space)
        assertEquals(ProductCategory.SANITARY, need.category)
        assertEquals("moderno", need.style)
        assertEquals("economico_moderado", need.budgetLevel)
    }

    @Test
    fun `confirmation belongs to one need and its revision`() {
        accept(IntentAnalysis(AssistanceType.CATEGORY_BROWSE, category = ProductCategory.SANITARY))
        val detail = session.prepareChange(IntentAnalysis(AssistanceType.CATEGORY_BROWSE, style = "moderno"))
        assertFalse(detail.requiresConfirmation)
        session.commitPending(markConfirmed = true)

        // un producto nuevo no viene confirmado por el sanitario
        val taps = session.prepareChange(IntentAnalysis(AssistanceType.CATEGORY_BROWSE, category = ProductCategory.TAPS))
        assertTrue(taps.requiresConfirmation)

        // un cambio importante en la misma necesidad vuelve a pedir confirmación
        session.discardPending()
        val zone = session.prepareChange(IntentAnalysis(AssistanceType.LOCATION_ONLY))
        assertTrue(zone.requiresConfirmation)
    }

    @Test
    fun `a null value never erases saved data`() {
        accept(IntentAnalysis(AssistanceType.CATEGORY_BROWSE, category = ProductCategory.TAPS, color = "negro", maxPrice = 90000.0))
        accept(IntentAnalysis(AssistanceType.CATEGORY_BROWSE))

        val taps = session.current().activeNeed!!
        assertEquals("negro", taps.color)
        assertEquals(90000.0, taps.maxPrice!!, 0.0)
    }

    @Test
    fun `product names are not kept as technical needs`() {
        accept(IntentAnalysis(AssistanceType.CATEGORY_BROWSE, category = ProductCategory.TAPS, space = "baño",
            technicalNeeds = listOf("sanitario", "baño", "monocontrol", "ahorradora")))

        assertEquals(setOf("ahorradora"), session.current().activeNeed!!.technicalNeeds)
    }

    @Test
    fun `revisions grow only when the need changes`() {
        accept(IntentAnalysis(AssistanceType.CATEGORY_BROWSE, category = ProductCategory.TAPS))
        val first = session.current().activeNeed!!.revision
        accept(IntentAnalysis(AssistanceType.CATEGORY_BROWSE))
        assertEquals(first, session.current().activeNeed!!.revision)
        accept(IntentAnalysis(AssistanceType.CATEGORY_BROWSE, color = "negro"))
        assertNotEquals(first, session.current().activeNeed!!.revision)
    }

    @Test
    fun `idle session is detected only after the timeout`() {
        session.prepareChange(IntentAnalysis(AssistanceType.CATEGORY_BROWSE, category = ProductCategory.TAPS), nowMs = 1_000L)

        assertFalse(session.isIdleFor(60_000L, nowMs = 30_000L))
        assertTrue(session.isIdleFor(60_000L, nowMs = 61_000L))
    }

    @Test
    fun `reset clears the customer and starts a new session`() {
        accept(IntentAnalysis(AssistanceType.CATEGORY_BROWSE, category = ProductCategory.SANITARY))
        val epoch = session.epoch

        session.reset()

        assertFalse(session.hasActiveCustomer())
        assertNotEquals(epoch, session.epoch)
    }
}
