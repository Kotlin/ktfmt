// PRESERVE_LAMBDA_BREAKS true
// BLOCK_INDENT 2
// CONTINUATION_INDENT 4

fun compose() {
  App {
    val state = remember { mutableStateOf(0) }
    SelectableCard {
      Button { Text("Count: ${'$'}{state.value}") }
    }
  }
}
