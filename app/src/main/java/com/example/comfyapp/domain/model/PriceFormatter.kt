// da formato de pesos colombianos a los precios del catalogo (686900.0 -> $686.900)
package com.example.comfyapp.domain.model

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

object PriceFormatter {

    private val format = DecimalFormat("#,##0", DecimalFormatSymbols(Locale("es", "CO")))

    fun format(price: Double): String = "$" + synchronized(format) { format.format(price) }
}
