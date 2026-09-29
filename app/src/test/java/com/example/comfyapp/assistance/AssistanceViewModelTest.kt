// verifica la conversacion completa: saludo, analisis, confirmacion, correccion y reintentos
package com.example.comfyapp.assistance

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.example.comfyapp.domain.model.AssistanceAction
import com.example.comfyapp.domain.model.AssistanceType
import com.example.comfyapp.domain.model.CatalogDestination
import com.example.comfyapp.domain.model.ConversationStage
import com.example.comfyapp.domain.model.CustomerContext
import com.example.comfyapp.domain.model.IntentAnalysis
import com.example.comfyapp.domain.model.ProductCategory
import com.example.comfyapp.domain.model.ProductType
import com.example.comfyapp.domain.repository.IntentAnalyzer
import com.example.comfyapp.session.CustomerSessionManager
import com.example.comfyapp.ui.products.category.assistance.AssistanceEffect
import com.example.comfyapp.ui.products.category.assistance.AssistanceViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class AssistanceViewModelTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private val analyzer = FakeIntentAnalyzer()
    private val events = mutableListOf<String>()
    private lateinit var viewModel: AssistanceViewModel

    @Before
    fun setUp() {
        CustomerSessionManager.reset()
        viewModel = AssistanceViewModel(analyzer, logEvent = { name, _ -> events += name })
    }

    @Test
    fun `new customer is greeted and heard`() {
        assertTrue(viewModel.start(fromDetection = true))

        assertEquals(AssistanceEffect.AskAndListen(AssistanceViewModel.GREETING), viewModel.effect.value)
        assertEquals(1, analyzer.resets)
        assertTrue("session_started" in events)
    }

    @Test
    fun `detection does not restart a conversation in progress`() {
        viewModel.start(fromDetection = true)

        assertFalse(viewModel.start(fromDetection = true))
    }

    @Test
    fun `category is confirmed before opening it`() {
        talk(IntentAnalysis(AssistanceType.CATEGORY_BROWSE, category = ProductCategory.SANITARY))

        val confirm = viewModel.effect.value as AssistanceEffect.Confirm
        assertEquals("Entendí que buscas sanitarios. ¿Es correcto?", confirm.summary)
        assertEquals(ConversationStage.CONFIRMING, viewModel.stage)

        viewModel.onConfirmed()

        val execute = viewModel.effect.value as AssistanceEffect.Execute
        assertEquals(AssistanceAction.StartCategoryFlow(CatalogDestination.SANITARY), execute.action)
        assertTrue(CustomerSessionManager.isConfirmed())
    }

    @Test
    fun `exact search confirmation says where it will look`() {
        talk(
            IntentAnalysis(
                AssistanceType.EXACT_PRODUCT,
                category = ProductCategory.TAPS,
                exactProductQuery = "coral"
            )
        )

        val confirm = viewModel.effect.value as AssistanceEffect.Confirm
        assertEquals("Entendí que buscas coral en griferías. ¿Es correcto?", confirm.summary)
    }

    @Test
    fun `confirmation states the price limit`() {
        talk(
            IntentAnalysis(
                AssistanceType.CATEGORY_BROWSE,
                category = ProductCategory.TAPS,
                space = "baño",
                budgetLevel = "economico",
                maxPrice = 100000.0
            )
        )

        val confirm = viewModel.effect.value as AssistanceEffect.Confirm
        assertEquals("Entendí que buscas griferías para baño, de menos de $100.000. ¿Es correcto?", confirm.summary)
    }

    @Test
    fun `confirmation names the product type`() {
        talk(
            IntentAnalysis(
                AssistanceType.CATEGORY_BROWSE,
                category = ProductCategory.SANITARY,
                productType = ProductType.COMBO,
                maxPrice = 500000.0
            )
        )

        val confirm = viewModel.effect.value as AssistanceEffect.Confirm
        assertEquals("Entendí que buscas combos de sanitario, de menos de $500.000. ¿Es correcto?", confirm.summary)
    }

    @Test
    fun `confirmation does not repeat space or type as technical needs`() {
        talk(
            IntentAnalysis(
                AssistanceType.TECHNICAL_NEED,
                category = ProductCategory.FLOOR_AND_WALL,
                space = "exterior",
                productType = ProductType.PAREDES,
                technicalNeeds = listOf("exterior", "paredes", "antideslizante")
            )
        )

        val confirm = viewModel.effect.value as AssistanceEffect.Confirm
        assertEquals(
            "Entendí que buscas revestimientos solo para paredes para exterior, antideslizante. ¿Es correcto?",
            confirm.summary
        )
    }

    @Test
    fun `advisor is requested immediately without confirmation`() {
        talk(IntentAnalysis(AssistanceType.HUMAN_ADVISOR, project = "remodelación"))

        val execute = viewModel.effect.value as AssistanceEffect.Execute
        assertEquals(AssistanceAction.RequestAdvisor, execute.action)
        assertEquals(ConversationStage.WAITING_ADVISOR, viewModel.stage)
    }

    @Test
    fun `project conversation keeps context until the category arrives`() {
        talk(IntentAnalysis(AssistanceType.PROJECT_ASSISTANCE, project = "remodelación", space = "baño"))
        val ask = viewModel.effect.value as AssistanceEffect.AskAndListen
        assertTrue(ask.question.contains("para baño"))

        answer(IntentAnalysis(AssistanceType.CATEGORY_BROWSE, category = ProductCategory.SANITARY))

        val confirm = viewModel.effect.value as AssistanceEffect.Confirm
        assertEquals("Entendí que buscas sanitarios para baño. ¿Es correcto?", confirm.summary)
        assertEquals("remodelación", CustomerSessionManager.current().project)
        // Temi le pasa a Gemini lo que ya sabe y lo que preguntó
        assertEquals("baño", analyzer.lastContext?.space)
        assertEquals(ask.question, analyzer.lastQuestion)
    }

    @Test
    fun `correction listens again and merges only the changed field`() {
        talk(
            IntentAnalysis(
                AssistanceType.CATEGORY_BROWSE,
                category = ProductCategory.TAPS,
                space = "cocina",
                color = "negro"
            )
        )
        viewModel.onCorrectionRequested()
        assertEquals(
            AssistanceEffect.AskAndListen(AssistanceViewModel.CORRECTION_QUESTION),
            viewModel.effect.value
        )

        answer(IntentAnalysis(AssistanceType.CATEGORY_BROWSE, color = "blanco"))

        val confirm = viewModel.effect.value as AssistanceEffect.Confirm
        assertEquals("Entendí que buscas griferías para cocina, color blanco. ¿Es correcto?", confirm.summary)
    }

    @Test
    fun `confirmed customer adding a detail is not asked again`() {
        talk(IntentAnalysis(AssistanceType.CATEGORY_BROWSE, category = ProductCategory.SANITARY))
        viewModel.onConfirmed()
        viewModel.onExecutionFinished()

        viewModel.start(fromDetection = false)
        assertEquals(
            AssistanceEffect.AskAndListen(AssistanceViewModel.FOLLOW_UP_QUESTION),
            viewModel.effect.value
        )
        answer(IntentAnalysis(AssistanceType.CATEGORY_BROWSE, style = "moderno"))

        val execute = viewModel.effect.value as AssistanceEffect.Execute
        assertEquals(AssistanceAction.StartCategoryFlow(CatalogDestination.SANITARY), execute.action)
        assertEquals("moderno", execute.context.style)
    }

    @Test
    fun `two failed attempts give up`() {
        viewModel.start(fromDetection = true)
        viewModel.onListeningStarted()
        viewModel.onNothingHeard()
        assertEquals(AssistanceEffect.AskAndListen(AssistanceViewModel.RETRY_QUESTION), viewModel.effect.value)

        viewModel.onListeningStarted()
        viewModel.onNothingHeard()

        assertEquals(AssistanceEffect.GiveUp(AssistanceViewModel.FINAL_ATTEMPT_TEXT), viewModel.effect.value)
        assertEquals(ConversationStage.IDLE, viewModel.stage)
    }

    @Test
    fun `gemini failure counts as a failed attempt`() {
        viewModel.start(fromDetection = true)
        viewModel.onListeningStarted()
        analyzer.next = Result.failure(IllegalStateException("timeout"))
        viewModel.onHeard("hola")

        assertEquals(AssistanceEffect.AskAndListen(AssistanceViewModel.RETRY_QUESTION), viewModel.effect.value)
    }

    @Test
    fun `empty clarification asks the suggested question`() {
        talk(IntentAnalysis(AssistanceType.CLARIFICATION, needsClarification = true, nextQuestion = "¿Para qué espacio?"))

        assertEquals(AssistanceEffect.AskAndListen("¿Para qué espacio?"), viewModel.effect.value)
    }

    @Test
    fun `follow up from the coordinator is asked and listened`() {
        talk(IntentAnalysis(AssistanceType.UNSUPPORTED))
        viewModel.onExecutionFinished("¿Qué te gustaría ver?")

        assertEquals(AssistanceEffect.AskAndListen("¿Qué te gustaría ver?"), viewModel.effect.value)
    }

    @Test
    fun `conversation interrupted without answer does not block the agent forever`() {
        var now = 1_000_000L
        viewModel = AssistanceViewModel(analyzer, logEvent = { name, _ -> events += name }, clock = { now })
        viewModel.start(fromDetection = true)
        viewModel.onListeningStarted()
        // la escucha nunca devuelve resultado (otra voz apagó el micrófono)

        assertFalse(viewModel.start(fromDetection = true))
        now += AssistanceViewModel.STALE_CONVERSATION_MS
        assertTrue(viewModel.start(fromDetection = true))
        assertTrue("conversation_stale_reset" in events)
    }

    @Test
    fun `talk button keeps an idle customer`() {
        CustomerSessionManager.merge(
            IntentAnalysis(AssistanceType.CATEGORY_BROWSE, category = ProductCategory.SANITARY),
            nowMs = System.currentTimeMillis() - AssistanceViewModel.SESSION_IDLE_TIMEOUT_MS - 1
        )

        viewModel.start(fromDetection = false)

        assertEquals(ProductCategory.SANITARY, CustomerSessionManager.current().category)
        assertEquals(AssistanceEffect.AskAndListen(AssistanceViewModel.FOLLOW_UP_QUESTION), viewModel.effect.value)
    }

    @Test
    fun `returning customer idle at centro sala starts over`() {
        CustomerSessionManager.merge(
            IntentAnalysis(AssistanceType.CATEGORY_BROWSE, category = ProductCategory.SANITARY),
            nowMs = System.currentTimeMillis() - AssistanceViewModel.SESSION_IDLE_TIMEOUT_MS - 1
        )

        viewModel.start(fromDetection = true)

        assertEquals(CustomerContext(), CustomerSessionManager.current())
        assertEquals(AssistanceEffect.AskAndListen(AssistanceViewModel.GREETING), viewModel.effect.value)
    }

    private fun talk(analysis: IntentAnalysis) {
        viewModel.start(fromDetection = true)
        answer(analysis)
    }

    private fun answer(analysis: IntentAnalysis) {
        viewModel.onListeningStarted()
        analyzer.next = Result.success(analysis)
        viewModel.onHeard("frase del cliente")
    }
}

private class FakeIntentAnalyzer : IntentAnalyzer {
    var next: Result<IntentAnalysis> = Result.failure(IllegalStateException("sin respuesta"))
    var resets = 0
    var lastContext: CustomerContext? = null
    var lastQuestion: String? = null

    override fun analyze(
        text: String,
        context: CustomerContext,
        lastQuestion: String?,
        callback: (Result<IntentAnalysis>) -> Unit
    ) {
        lastContext = context
        this.lastQuestion = lastQuestion
        callback(next)
    }

    override fun reset() {
        resets++
    }
}
