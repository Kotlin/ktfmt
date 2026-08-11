context(Something)
class A {
  context(
  // Test comment.
  Logger,
  Raise<Error>)
  @SomeAnnotation
  fun doNothing() {}

  context(SomethingElse)
  private class NestedClass {}

  fun <T> testSuspend(
      mock: T,
      block: suspend context(SomeContext) T.() -> Unit,
  ) = startCoroutine {
    T.block()
  }
}

class B(val x: Int) {
  context(Something)
  constructor() : this(0)
}
