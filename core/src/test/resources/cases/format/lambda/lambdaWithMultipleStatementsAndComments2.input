private val a = Runnable { /* no-op */ }
private val A = Runnable { TODO("...") }

private val b = Runnable {
  /* no-op 1 */
  /* no-op 2 */
}
private val B = Runnable {
  TODO("no-op")
  TODO("no-op")
}

private val c: () -> Unit = { /* no-op */ }
private val C: () -> Unit = { TODO("...") }

private val d: () -> Unit = {
  /*.*/
  /* do nothing ... */
}
private val D: () -> Unit = {
  foo()
  TODO("implement me")
}
