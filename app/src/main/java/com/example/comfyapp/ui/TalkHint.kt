// hace aparecer el aviso "toca aquí para hablar con temi" junto al botón flotante cada pocos segundos
package com.example.comfyapp.ui

import android.view.View

object TalkHint {

    private const val CYCLE_MS = 3_000L
    private const val FADE_MS = 300L
    private const val VISIBLE_MS = 1_800L

    // Solo corre mientras la vista está en pantalla: al cerrarse la actividad se detiene solo.
    fun attach(hint: View) {
        val cycle = object : Runnable {
            override fun run() {
                if (!hint.isAttachedToWindow) return
                // El animador se reutiliza: el retraso del desvanecido anterior se limpia aquí.
                hint.animate().alpha(1f).setStartDelay(0).setDuration(FADE_MS).withEndAction {
                    hint.animate().alpha(0f).setStartDelay(VISIBLE_MS).setDuration(FADE_MS).start()
                }.start()
                hint.postDelayed(this, CYCLE_MS)
            }
        }
        hint.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View) {
                view.removeCallbacks(cycle)
                view.post(cycle)
            }

            override fun onViewDetachedFromWindow(view: View) {
                view.removeCallbacks(cycle)
                view.animate().cancel()
            }
        })
        if (hint.isAttachedToWindow) hint.post(cycle)
    }
}
