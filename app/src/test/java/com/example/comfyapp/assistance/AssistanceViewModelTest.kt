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
import com.example.comfyapp.domain.model.NeedOperation
import com.example.comfyapp.domain.model.ProductType
import com.example.comfyapp.domain.model.RequestedNeed
import com.example.comfyapp.domain.repository.AnalysisRequest
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
        assertTrue(CustomerSessionManager.current().activeNeed!!.isConfirmed)
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
        // sin confirmar todavía: el proyecto vive en el borrador, no en lo aceptado
        assertEquals("remodelación", CustomerSessionManager.pending()?.candidate?.project)
        assertEquals(null, CustomerSessionManager.current().project)
        viewModel.onConfirmed()
        assertEquals("remodelación", CustomerSessionManager.current().project)
        // Temi le pasa a Gemini el borrador que se está completando y lo que preguntó
        assertEquals("baño", analyzer.lastRequest?.draft?.need?.space)
        assertEquals(ask.question, analyzer.lastRequest?.lastQuestion)
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
        assertEquals("moderno", execute.context.activeNeed?.style)
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
        acceptOld(IntentAnalysis(AssistanceType.CATEGORY_BROWSE, category = ProductCategory.SANITARY))

        viewModel.start(fromDetection = false)

        assertEquals(ProductCategory.SANITARY, CustomerSessionManager.current().activeNeed?.category)
        assertEquals(AssistanceEffect.AskAndListen(AssistanceViewModel.FOLLOW_UP_QUESTION), viewModel.effect.value)
    }

    @Test
    fun `returning customer idle at centro sala starts over`() {
        acceptOld(IntentAnalysis(AssistanceType.CATEGORY_BROWSE, category = ProductCategory.SANITARY))

        viewModel.start(fromDetection = true)

        assertEquals(CustomerContext(), CustomerSessionManager.current())
        assertEquals(AssistanceEffect.AskAndListen(AssistanceViewModel.GREETING), viewModel.effect.value)
    }

    @Test
    fun `a second product is confirmed on its own and does not inherit the first one`() {
        talk(
            IntentAnalysis(
                AssistanceType.CATEGORY_BROWSE, category = ProductCategory.SANITARY, space = "baño",
                technicalNeeds = listOf("ahorrador"), maxPrice = 500000.0
            )
        )
        viewModel.onConfirmed()
        viewModel.onExecutionFinished()

        viewModel.start(fromDetection = false)
        answer(IntentAnalysis(AssistanceType.CATEGORY_BROWSE, category = ProductCategory.TAPS, space = "baño"))

        val confirm = viewModel.effect.value as AssistanceEffect.Confirm
        assertEquals("Entendí que buscas griferías para baño. ¿Es correcto?", confirm.summary)
        viewModel.onConfirmed()
        val execute = viewModel.effect.value as AssistanceEffect.Execute
        assertEquals(ProductCategory.TAPS, execute.context.activeNeed?.category)
        assertEquals(null, execute.context.activeNeed?.maxPrice)
        assertEquals(2, execute.context.needs.size)
    }

    @Test
    fun `a rejected interpretation never reaches the accepted memory`() {
        talk(IntentAnalysis(AssistanceType.CATEGORY_BROWSE, category = ProductCategory.SANITARY, space = "baño"))
        viewModel.onCorrectionRequested()
        assertTrue(CustomerSessionManager.current().needs.isEmpty())
        assertEquals(1, analyzer.forgotten)

        answer(IntentAnalysis(AssistanceType.CATEGORY_BROWSE, category = ProductCategory.TAPS))

        assertTrue(analyzer.lastRequest!!.correcting)
        val confirm = viewModel.effect.value as AssistanceEffect.Confirm
        assertEquals("Entendí que buscas griferías para baño. ¿Es correcto?", confirm.summary)
        viewModel.onConfirmed()
        assertEquals(listOf(ProductCategory.TAPS), CustomerSessionManager.current().needs.map { it.category })
    }

    @Test
    fun `an expired confirmation discards the draft`() {
        talk(IntentAnalysis(AssistanceType.CATEGORY_BROWSE, category = ProductCategory.SANITARY))
        viewModel.onConfirmationTimeout()

        assertTrue(CustomerSessionManager.current().needs.isEmpty())
        assertEquals(null, CustomerSessionManager.pending())
    }

    @Test
    fun `a late answer from an old request is ignored`() {
        analyzer.deferred = true
        viewModel.start(fromDetection = true)
        viewModel.onListeningStarted()
        viewModel.onHeard("primera frase")
        val oldCallback = analyzer.pendingCallback!!
        viewModel.cancel()

        viewModel.start(fromDetection = false)
        viewModel.onListeningStarted()
        viewModel.onHeard("segunda frase")
        oldCallback(Result.success(IntentAnalysis(AssistanceType.CATEGORY_BROWSE, category = ProductCategory.SANITARY)))

        assertEquals(ConversationStage.ANALYZING, viewModel.stage)
        assertTrue("intent_ignored" in events)
    }

    @Test
    fun `two products in one phrase confirm the first and note the second`() {
        talk(
            IntentAnalysis(
                AssistanceType.CATEGORY_BROWSE, category = ProductCategory.SANITARY, space = "baño",
                additionalNeeds = listOf(RequestedNeed(category = ProductCategory.TAPS, space = "baño"))
            )
        )

        val confirm = viewModel.effect.value as AssistanceEffect.Confirm
        assertEquals(
            "Entendí que buscas sanitarios para baño. También anoté griferías para baño para revisarlo después. ¿Es correcto?",
            confirm.summary
        )
    }

    @Test
    fun `an unclear reference asks which saved need`() {
        talk(IntentAnalysis(AssistanceType.CATEGORY_BROWSE, category = ProductCategory.SANITARY, space = "baño principal"))
        viewModel.onConfirmed(); viewModel.onExecutionFinished()
        viewModel.start(fromDetection = false)
        answer(IntentAnalysis(AssistanceType.CATEGORY_BROWSE, needOperation = NeedOperation.ADD_NEED, space = "baño de visitas"))
        viewModel.onConfirmed(); viewModel.onExecutionFinished()
        viewModel.start(fromDetection = false)
        answer(IntentAnalysis(AssistanceType.CATEGORY_BROWSE, category = ProductCategory.TAPS))
        viewModel.onConfirmed(); viewModel.onExecutionFinished()

        viewModel.start(fromDetection = false)
        answer(IntentAnalysis(AssistanceType.CATEGORY_BROWSE, needOperation = NeedOperation.RESUME_NEED, category = ProductCategory.SANITARY))

        val ask = viewModel.effect.value as AssistanceEffect.AskAndListen
        assertEquals(
            "Tengo anotados varios. ¿Te refieres a sanitarios para baño principal o a sanitarios para baño de visitas?",
            ask.question
        )
    }

    private fun acceptOld(analysis: IntentAnalysis) {
        val old = System.currentTimeMillis() - AssistanceViewModel.SESSION_IDLE_TIMEOUT_MS - 1
        CustomerSessionManager.prepareChange(analysis, nowMs = old)
        CustomerSessionManager.commitPending(markConfirmed = true, nowMs = old)
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
    var forgotten = 0
    var lastRequest: AnalysisRequest? = null
    // si es true la respuesta no llega sola: la prueba decide cuándo (y si) llamar al callback
    var deferred = false
    var pendingCallback: ((Result<IntentAnalysis>) -> Unit)? = null

    override fun analyze(request: AnalysisRequest, callback: (Result<IntentAnalysis>) -> Unit) {
        lastRequest = request
        if (deferred) pendingCallback = callback else callback(next)
    }

    override fun forgetLastTurn() {
        forgotten++
    }

    override fun reset() {
        resets++
    }
}
