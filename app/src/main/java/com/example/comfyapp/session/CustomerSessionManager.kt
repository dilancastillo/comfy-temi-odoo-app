// conserva en memoria las necesidades aceptadas del cliente y el borrador pendiente, separado del estado del robot
package com.example.comfyapp.session

import com.example.comfyapp.domain.model.CustomerContext
import com.example.comfyapp.domain.model.CustomerNeed
import com.example.comfyapp.domain.model.IntentAnalysis
import com.example.comfyapp.domain.model.NeedOperation
import com.example.comfyapp.domain.model.PendingNeedChange

object CustomerSessionManager {

    // lo aceptado por el cliente: única fuente de verdad para consultas, resumen y asesor
    private var context = CustomerContext()
    // lo que Temi entendió y aún no se confirmó (o sigue completándose con aclaraciones)
    private var pending: PendingNeedChange? = null
    private var needSequence = 0
    private var lastActivityAtMs = 0L
    // cambia con cada cliente nuevo: una respuesta de Gemini de la sesión anterior se descarta
    @Volatile var epoch = 0L
        private set

    @Synchronized
    fun current(): CustomerContext = context

    @Synchronized
    fun pending(): PendingNeedChange? = pending

    @Synchronized
    fun hasActiveCustomer(): Boolean = !context.isEmpty || pending != null

    @Synchronized
    fun isIdleFor(timeoutMs: Long, nowMs: Long = System.currentTimeMillis()): Boolean =
        hasActiveCustomer() && nowMs - lastActivityAtMs >= timeoutMs

    // Arma el borrador del turno sin tocar lo aceptado. Si ya había un borrador (aclaraciones o
    // corrección) se sigue completando ese mismo.
    @Synchronized
    fun prepareChange(
        analysis: IntentAnalysis,
        correcting: Boolean = false,
        nowMs: Long = System.currentTimeMillis()
    ): PendingNeedChange {
        lastActivityAtMs = nowMs
        val draft = pending
        val base = draft?.candidate ?: context
        val active = base.activeNeed
        val target = resolveTarget(analysis, base, active, correcting && draft?.need != null)

        // Sin un destino claro no se guarda nada: primero se pregunta (el borrador anterior sigue igual).
        when (target) {
            is Target.Ambiguous -> return PendingNeedChange(
                NeedOperation.RESUME_NEED, base, null, null, emptySet(), ambiguousNeeds = target.options
            )
            Target.NotFound -> return PendingNeedChange(
                NeedOperation.RESUME_NEED, base, null, null, emptySet(), resumeNotFound = true
            )
            else -> Unit
        }

        val (operation, before, applied) = when (target) {
            // "Otro para el baño de visitas": la nueva necesidad toma la categoría de la activa si no la nombró.
            Target.New -> Triple(
                NeedOperation.ADD_NEED,
                CustomerNeed(id = nextNeedId()),
                analysis.copy(category = analysis.category ?: active?.category)
            )
            Target.CorrectDraft -> Triple(NeedOperation.CORRECT_PENDING_NEED, draft!!.need!!, analysis)
            is Target.Existing -> Triple(
                if (target.need.id == active?.id) NeedOperation.UPDATE_ACTIVE_NEED else NeedOperation.RESUME_NEED,
                target.need,
                analysis
            )
            else -> error("destino sin resolver")
        }

        val changed = before.applyChange(applied)
        val accepted = context.need(changed.id)
        val need = when {
            changed.isBlank -> null
            accepted != null && changed.sameContentAs(accepted) -> accepted
            changed.sameContentAs(before) && before.id == changed.id && before.revision > 0 -> before
            else -> changed.copy(
                revision = (accepted?.revision ?: 0) + 1,
                confirmedRevision = accepted?.confirmedRevision ?: CustomerNeed.NOT_CONFIRMED
            )
        }
        val withProject = base.copy(project = analysis.project?.trim()?.takeIf { it.isNotEmpty() } ?: base.project)
        var candidate = need?.let(withProject::withActiveNeed) ?: withProject

        // Otros productos de la misma frase: cada uno es su propia necesidad, sin volverse la activa.
        val additional = if (operation == NeedOperation.CORRECT_PENDING_NEED) {
            draft?.additionalNeeds.orEmpty()
        } else {
            analysis.additionalNeeds
                .map { CustomerNeed(id = nextNeedId()).applyChange(it.asAnalysis()).copy(revision = 1) }
                .filterNot { it.isBlank }
        }
        if (operation != NeedOperation.CORRECT_PENDING_NEED && additional.isNotEmpty()) {
            candidate = candidate.copy(needs = candidate.needs + additional)
        }

        var changedFields = (need ?: before).changedFieldsFrom(before.takeUnless { operation == NeedOperation.ADD_NEED })
        if (candidate.project != base.project) changedFields = changedFields + "project"
        if (candidate.activeNeedId != base.activeNeedId) changedFields = changedFields + "activeNeed"
        if (additional.isNotEmpty() && operation != NeedOperation.CORRECT_PENDING_NEED) {
            changedFields = changedFields + "additionalNeeds"
        }

        return PendingNeedChange(
            operation = operation,
            candidate = candidate,
            need = need,
            accepted = accepted,
            changedFields = changedFields,
            additionalNeeds = additional
        ).also { pending = it }
    }

    // El cliente aceptó (o no hacía falta confirmar): el borrador pasa a ser la memoria aceptada.
    // markConfirmed deja confirmada la revisión guardada de esa necesidad.
    @Synchronized
    fun commitPending(markConfirmed: Boolean, nowMs: Long = System.currentTimeMillis()) {
        val change = pending ?: return
        val need = change.need?.let { if (markConfirmed) it.copy(confirmedRevision = it.revision) else it }
        context = need?.let(change.candidate::withActiveNeed) ?: change.candidate
        pending = null
        lastActivityAtMs = nowMs
    }

    // Cancelado, vencido o rechazado: se olvida el borrador y lo aceptado queda igual.
    @Synchronized
    fun discardPending() {
        pending = null
    }

    @Synchronized
    fun markAdvisorRequested() {
        context = context.copy(advisorRequested = true)
    }

    @Synchronized
    fun setCurrentLocation(location: String) {
        context = context.copy(currentLocation = location)
    }

    // Ajuste hecho por la app sobre la necesidad activa aceptada (ej. la referencia no existe).
    @Synchronized
    fun updateActiveNeed(transform: (CustomerNeed) -> CustomerNeed) {
        val active = context.activeNeed ?: return
        context = context.withActiveNeed(transform(active).copy(revision = active.revision + 1))
    }

    @Synchronized
    fun touch(nowMs: Long = System.currentTimeMillis()) {
        lastActivityAtMs = nowMs
    }

    @Synchronized
    fun reset() {
        context = CustomerContext()
        pending = null
        needSequence = 0
        lastActivityAtMs = 0L
        epoch++
    }

    private sealed interface Target {
        data object New : Target
        data object CorrectDraft : Target
        data class Existing(val need: CustomerNeed) : Target
        data class Ambiguous(val options: List<CustomerNeed>) : Target
        data object NotFound : Target
    }

    // Gemini propone la operación y a qué necesidad se refiere, pero el destino lo decide el código:
    // - durante una corrección, el cambio va al borrador pendiente;
    // - un id de necesidad solo vale si existe en esta sesión;
    // - retomar busca entre las guardadas: una sola coincidencia se retoma, varias se preguntan y
    //   ninguna se avisa sin crear ni modificar otra;
    // - "otro"/"también" (ADD_NEED) crea otra necesidad, aunque sea de la misma categoría;
    // - nombrar otra categoría retoma la guardada de esa categoría o, si no hay, crea una nueva;
    // - lo demás modifica la activa.
    private fun resolveTarget(
        analysis: IntentAnalysis,
        base: CustomerContext,
        active: CustomerNeed?,
        correcting: Boolean
    ): Target {
        if (correcting) return Target.CorrectDraft
        val operation = analysis.needOperation
        val others = base.needs.filter { it.id != active?.id }

        analysis.targetNeedId?.let(base::need)?.let { return Target.Existing(it) }

        val category = analysis.category
        if (operation == NeedOperation.RESUME_NEED) {
            val matches = if (category != null) others.filter { it.category == category } else others
            return when {
                matches.size == 1 -> Target.Existing(matches.single())
                matches.size > 1 -> Target.Ambiguous(matches)
                // "sigamos con eso" sin otra guardada: se queda en la activa
                category == null && active != null -> Target.Existing(active)
                else -> Target.NotFound
            }
        }
        if (operation == NeedOperation.ADD_NEED && (category != null || active?.category != null)) return Target.New
        if (active == null) return Target.New
        if (category != null && active.category != null && category != active.category) {
            val matches = others.filter { it.category == category }
            return when (matches.size) {
                0 -> Target.New
                1 -> Target.Existing(matches.single())
                else -> Target.Ambiguous(matches)
            }
        }
        return Target.Existing(active)
    }

    private fun nextNeedId(): String = "need_%02d".format(++needSequence)
}
