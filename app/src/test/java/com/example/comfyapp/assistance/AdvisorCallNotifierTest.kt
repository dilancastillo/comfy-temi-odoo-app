package com.example.comfyapp.assistance

import com.example.comfyapp.core.AdvisorCallNotifier
import com.example.comfyapp.core.AdvisorCallNotifier.Contact
import com.example.comfyapp.core.AdvisorCallNotifier.Result
import com.robotemi.sdk.telepresence.CallState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdvisorCallNotifierTest {

    private class FakeScheduler : AdvisorCallNotifier.Scheduler {
        var now = 0L
        private val tasks = mutableListOf<Triple<Long, Any, () -> Unit>>()

        override fun schedule(delayMs: Long, block: () -> Unit): Any =
            Any().also { tasks += Triple(now + delayMs, it, block) }

        override fun cancel(token: Any) {
            tasks.removeAll { it.second === token }
        }

        fun advance(ms: Long) {
            val target = now + ms
            while (true) {
                val next = tasks.filter { it.first <= target }.minByOrNull { it.first } ?: break
                tasks.remove(next)
                now = next.first
                next.third()
            }
            now = target
        }
    }

    private class FakeGateway(
        var contacts: List<Contact>,
        var permission: Boolean = true,
        var failingIds: Set<String> = emptySet()
    ) : AdvisorCallNotifier.Gateway {
        val calls = mutableListOf<String>()
        val tilts = mutableListOf<Int>()
        var hangUps = 0
        var listener: ((CallState.State) -> Unit)? = null

        override fun hasPermission() = permission
        override fun contacts() = contacts
        override fun call(contact: Contact): String {
            calls += contact.name
            return if (contact.id in failingIds) "" else "session-${calls.size}"
        }
        override fun hangUp() { hangUps++ }
        override fun setCallListener(listener: ((CallState.State) -> Unit)?) { this.listener = listener }
        override fun tiltHead(degrees: Int) { tilts += degrees }

        fun emit(state: CallState.State) = listener?.invoke(state)
    }

    private val liliana = Contact("u1", "Liliana Pérez")
    private val camila = Contact("u2", "Camila")
    private val jorge = Contact("u3", "Jorge")

    private fun notifier(
        gateway: FakeGateway,
        scheduler: FakeScheduler,
        names: List<String> = listOf("liliana", "camila"),
        online: Boolean = true
    ) = AdvisorCallNotifier(gateway, scheduler, names, ringMs = 10_000, isOnline = { online }, logEvent = { _, _ -> })

    @Test
    fun firstContactAnswers_hangsUpImmediately_andReportsName() {
        val gateway = FakeGateway(listOf(jorge, camila, liliana))
        val scheduler = FakeScheduler()
        var result: Result? = null
        notifier(gateway, scheduler).notify { result = it }

        gateway.emit(CallState.State.INITIALIZED)
        scheduler.advance(4_000)
        gateway.emit(CallState.State.STARTED)

        assertEquals(Result.Answered("Liliana Pérez"), result)
        assertEquals(listOf("Liliana Pérez"), gateway.calls)
        assertEquals(1, gateway.hangUps)
        assertNull(gateway.listener)
        // la reunión sigue transmitiendo un rato después de colgar: la cabeza no baja todavía
        assertEquals(listOf(AdvisorCallNotifier.HEAD_UP_DEGREES), gateway.tilts)
        scheduler.advance(AdvisorCallNotifier.HEAD_HOLD_AFTER_CALL_MS)
        assertEquals(listOf(AdvisorCallNotifier.HEAD_UP_DEGREES, AdvisorCallNotifier.HEAD_REST_DEGREES), gateway.tilts)
    }

    @Test
    fun noAnswerIn10Seconds_callsNextContact() {
        val gateway = FakeGateway(listOf(liliana, camila))
        val scheduler = FakeScheduler()
        var result: Result? = null
        val notifier = notifier(gateway, scheduler)
        notifier.notify { result = it }

        scheduler.advance(9_999)
        assertEquals(listOf("Liliana Pérez"), gateway.calls)
        scheduler.advance(1)
        assertEquals(1, gateway.hangUps)
        // El ENDED de la llamada colgada no debe saltarse a Camila.
        gateway.emit(CallState.State.ENDED)
        scheduler.advance(AdvisorCallNotifier.BETWEEN_CALLS_MS)
        assertEquals(listOf("Liliana Pérez", "Camila"), gateway.calls)

        gateway.emit(CallState.State.STARTED)
        assertEquals(Result.Answered("Camila"), result)
        assertFalse(notifier.isBusy)
    }

    @Test
    fun declinedOrBusy_movesOnWithoutWaitingTheFullRing() {
        val gateway = FakeGateway(listOf(liliana, camila))
        val scheduler = FakeScheduler()
        notifier(gateway, scheduler).notify { }

        scheduler.advance(2_000)
        gateway.emit(CallState.State.DECLINED)
        scheduler.advance(AdvisorCallNotifier.BETWEEN_CALLS_MS)

        assertEquals(listOf("Liliana Pérez", "Camila"), gateway.calls)
    }

    @Test
    fun nobodyAnswers_reportsNoAnswer_once() {
        val gateway = FakeGateway(listOf(liliana, camila))
        val scheduler = FakeScheduler()
        val results = mutableListOf<Result>()
        val notifier = notifier(gateway, scheduler)
        notifier.notify { results += it }

        scheduler.advance(60_000)
        gateway.emit(CallState.State.STARTED) // llega tarde, ya no cuenta

        assertEquals(listOf<Result>(Result.NoAnswer(listOf("Liliana Pérez", "Camila"))), results)
        scheduler.advance(AdvisorCallNotifier.HEAD_HOLD_AFTER_CALL_MS)
        assertEquals(AdvisorCallNotifier.HEAD_REST_DEGREES, gateway.tilts.last())
        assertFalse(notifier.isBusy)
    }

    @Test
    fun callThatCannotStart_skipsToNextContact() {
        val gateway = FakeGateway(listOf(liliana, camila), failingIds = setOf("u1"))
        val scheduler = FakeScheduler()
        notifier(gateway, scheduler).notify { }

        scheduler.advance(AdvisorCallNotifier.BETWEEN_CALLS_MS)

        assertEquals(listOf("Liliana Pérez", "Camila"), gateway.calls)
    }

    @Test
    fun secondRequestWhileCalling_isRejected() {
        val gateway = FakeGateway(listOf(liliana))
        val notifier = notifier(gateway, FakeScheduler())

        assertTrue(notifier.notify { })
        assertFalse(notifier.notify { })
        assertEquals(1, gateway.calls.size)
    }

    @Test
    fun withoutPermissionContactsOrInternet_reportsUnavailable_withoutCalling() {
        fun reason(gateway: FakeGateway, names: List<String> = listOf("liliana"), online: Boolean = true): String? {
            var result: Result? = null
            notifier(gateway, FakeScheduler(), names, online).notify { result = it }
            assertTrue(gateway.calls.isEmpty())
            return (result as? Result.Unavailable)?.reason
        }
        assertEquals(AdvisorCallNotifier.REASON_NO_PERMISSION, reason(FakeGateway(listOf(liliana), permission = false)))
        assertEquals(AdvisorCallNotifier.REASON_NO_CONTACTS, reason(FakeGateway(listOf(jorge))))
        assertEquals(AdvisorCallNotifier.REASON_OFFLINE, reason(FakeGateway(listOf(liliana)), online = false))
        assertEquals(AdvisorCallNotifier.REASON_NOT_CONFIGURED, reason(FakeGateway(listOf(liliana)), names = emptyList()))
    }

    @Test
    fun resolveContacts_keepsConfiguredOrder_andIgnoresAccentsAndCase() {
        val contacts = listOf(Contact("a", "Camila Gómez"), Contact("b", "Liliana Pérez"), Contact("c", "Lili"))
        val resolved = AdvisorCallNotifier.resolveContacts(
            AdvisorCallNotifier.parseNames(" liliana perez , CAMILA, lili , nadie"),
            contacts
        )
        assertEquals(listOf("b", "a", "c"), resolved.map { it.id })
    }
}
