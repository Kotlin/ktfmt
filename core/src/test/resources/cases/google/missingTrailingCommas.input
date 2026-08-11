fun main() {
  fun <
    A,
    B // Comma before comment
  > foo() {}

  fun foo(
    a: Int,
    b: Int = 0 // Comma before comment
  ) {}

  foo<
    Int,
    Int // Comma before comment
  >()

  foo(
    0,
    b = 0 // Comma before comment
  )

  foo(
    0,
    b = {
      // Comma outside lambda
    }
  )

  @Anno(
    arr = [
      0,
      0 // Comma before comment
    ]
  )
  fun foo() {}
}
