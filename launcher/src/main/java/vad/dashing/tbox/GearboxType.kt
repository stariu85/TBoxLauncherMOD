package vad.dashing.tbox

import java.util.Locale

enum class GearboxType(val code: String) {
    DCT_7("7dct"),
    DCT_6("6dct"),
    MANUAL("manual");

    companion object {
        fun fromCode(code: String?): GearboxType = when (code?.lowercase(Locale.ROOT)) {
            DCT_6.code -> DCT_6
            MANUAL.code, "mt" -> MANUAL
            else -> DCT_7
        }
    }
}
