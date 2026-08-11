fun f() {
  var b
  @Suppress("UNCHECKED_CAST") b = f(1) as Int
  @Suppress("UNCHECKED_CAST")
  b = f(1) as Int

  @Suppress("UNCHECKED_CAST") b = f(1) to 5
  @Suppress("UNCHECKED_CAST")
  b = f(1) to 5

  @Suppress("UNCHECKED_CAST") f(1) as Int + 5
  @Suppress("UNCHECKED_CAST")
  f(1) as Int + 5

  @Anno1 /* comment */ @Anno2 f(1) as Int

  @Suppress("UNCHECKED_CAST") f(1 + f(1) as Int)
  @Suppress("UNCHECKED_CAST")
  f(1 + f(1) as Int)
}
