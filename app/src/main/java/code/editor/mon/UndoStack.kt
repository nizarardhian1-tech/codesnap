package code.editor.mon

class UndoStack(private val maxSize: Int = 50) {
    data class State(val text: String, val cursorPos: Int)

    private val stack = ArrayDeque<State>()
    private var lastSavedText = ""

    fun push(text: String, cursor: Int) {
        if (text == lastSavedText) return
        lastSavedText = text
        stack.addLast(State(text, cursor))
        if (stack.size > maxSize) stack.removeFirst()
    }

    fun pop(): State? {
        if (stack.size <= 1) return null
        stack.removeLast()
        return stack.lastOrNull()
    }

    fun canUndo() = stack.size > 1

    fun clear() {
        stack.clear()
        lastSavedText = ""
    }

    fun init(text: String) {
        clear()
        stack.addLast(State(text, 0))
        lastSavedText = text
    }
}
