package cz.playlingo

import android.text.InputFilter
import android.text.InputType
import android.widget.EditText

/**
 * Políčko pro adresu v domácí síti (192.168.1.20). Číselná klávesnice má v češtině místo tečky
 * čárku, proto se otevře klávesnice pro adresy (tečka bývá hned vedle mezery) a čárka nebo mezera
 * se samy změní na tečku; ostatní znaky kromě číslic se do políčka nedostanou.
 */
object IpField {
    fun setup(e: EditText) {
        e.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        e.filters = arrayOf(InputFilter { source, start, end, _, _, _ ->
            val sb = StringBuilder()
            for (i in start until end) {
                val c = source[i]
                when {
                    c in '0'..'9' || c == '.' -> sb.append(c)
                    c == ',' || c == ' ' -> sb.append('.')
                }
            }
            if (sb.length == end - start && sb.toString() == source.subSequence(start, end).toString()) null else sb.toString()
        })
    }
}
