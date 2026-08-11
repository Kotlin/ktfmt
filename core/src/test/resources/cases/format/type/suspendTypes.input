private val reader: suspend (Key) -> Output?

private val delete: (suspend (Key) -> Unit)? = null

inline fun <R> foo(noinline block: suspend () -> R): suspend () -> R

inline fun <R> bar(noinline block: (suspend () -> R)?): (suspend () -> R)?
