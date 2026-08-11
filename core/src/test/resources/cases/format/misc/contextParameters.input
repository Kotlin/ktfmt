context(something: Something)

class A {
  context(
  // Test comment.
  logger: Logger, raise: Raise<Error>, _: Ignored)

  @SomeAnnotation

  fun doNothing() {}

  context(somethingElse: SomethingElse)

  private class NestedClass {}

  fun <T> testSuspend(
    mock: T,
    block: suspend context(someContext: SomeContext) T.() -> Unit,
  ) = startCoroutine {
    T.block()
  }
}
