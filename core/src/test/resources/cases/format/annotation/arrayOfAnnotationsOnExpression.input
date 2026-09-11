fun foo() {
  @[Suppress("DEPRECATION") OptIn(ExperimentalContextReceivers::class)]
  println("Hello, world!")

  @[Suppress("DEPRECATION") OptIn(ExperimentalContextReceivers::class)]
  @C
  println("Hello, world!")

  @C
  @[Suppress("DEPRECATION") OptIn(ExperimentalContextReceivers::class)]
  println("Hello, world!")

  @[A B]
  @C
  println("Hello, world!")

  @C
  @[A B]
  println("Hello, world!")
}
