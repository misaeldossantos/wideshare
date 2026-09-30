package wideshare.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** Latest lines of the activity screen, each with its time. */
class LogBuffer {
    val lines = MutableStateFlow<List<String>>(emptyList())

    fun add(line: String) {
        val time = java.time.LocalTime.now().withNano(0)
        lines.update { (it + "$time  $line").takeLast(200) }
    }
}
