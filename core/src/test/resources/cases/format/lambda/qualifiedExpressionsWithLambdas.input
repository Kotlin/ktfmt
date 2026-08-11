// MAX_WIDTH 79
// TRAILING_COMMA_STRATEGY NONE

fun f() {
  val items =
      items.toMutableList.apply {
        //
        foo
      }

  val items =
      items.toMutableList().apply {
        //
        foo
      }

  // All dereferences are on one line (because they fit), even though
  // the apply() at the end requires a line break.
  val items =
      items.toMutableList.sdfkjsdf.sdfjksdflk.sdlfkjsldfj.apply {
        //
        foo
      }

  // All method calls are on one line (because they fit), even though
  // the apply() at the end requires a line break.
  val items =
      items.toMutableList().sdfkjsdf().sdfjksdflk().sdlfkjsldfj().apply {
        //
        foo
      }

  // All method calls with lambdas could fit, but we avoid a block like syntax
  // and break to one call per line
  val items =
      items
          .map { it + 1 }
          .filter { it > 0 }
          .apply {
            //
            foo
          }

  // the lambda is indented properly with the break before it
  val items =
      items.fieldName.sdfkjsdf.sdfjksdflk.sdlfkjsldfj.sdfjksdflk.sdlfkjsldfj
          .sdlfkjsldfj
          .apply {
            //
            foo
          }
  items.fieldName.sdfkjsdf.sdfjksdflk.sdlfkjsldfj.sdfjksdflk.sdlfkjsldfj
      .apply {
        //
        foo
      }

  // When there are multiple method calls, and they don't fit on one
  // line, put each on a new line.
  val items =
      items
          .toMutableList()
          .sdfkjsdf()
          .sdfjksdflk()
          .sdlfkjsldfj()
          .sdfjksdflk()
          .sdlfkjsldfj()
          .apply {
            //
            foo
          }
}
