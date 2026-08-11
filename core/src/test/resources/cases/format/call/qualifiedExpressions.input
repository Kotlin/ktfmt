// MAX_WIDTH 37
// TRAILING_COMMA_STRATEGY NONE

fun f() {
  // Regression test:
  // https://github.com/facebook/ktfmt/issues/56
  kjsdfglkjdfgkjdfkgjhkerjghkdfj
      ?.methodName1()

  // a series of field accesses
  // followed by a single call
  // expression is kept together.
  abcdefghijkl.abcdefghijkl
      ?.methodName2()

  // Similar to above.
  abcdefghijkl.abcdefghijkl
      ?.methodName3
      ?.abcdefghijkl()

  // Multiple call expressions cause
  // each part of the expression
  // to be placed on its own line.
  abcdefghijkl
      ?.abcdefghijkl
      ?.methodName4()
      ?.abcdefghijkl()

  // Don't break first call
  // expression if it fits.
  foIt(something.something.happens())
      .thenReturn(result)

  // Break after `longerThanFour(`
  // because it's longer than 4 chars
  longerThanFour(
          something.something
              .happens())
      .thenReturn(result)

  // Similarly to above, when part of
  // qualified expression.
  foo.longerThanFour(
          something.something
              .happens())
      .thenReturn(result)

  // Keep 'super' attached to the
  // method name
  super.abcdefghijkl
      .methodName4()
      .abcdefghijkl()
}
