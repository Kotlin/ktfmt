// MAX_WIDTH 32
// TRAILING_COMMA_STRATEGY NONE

fun stringFitsButNotMethod() {
  val str1 =
      """ Some string """
          .trimIndent()

  val str2 =
      """ Some string """
          .trimIndent(someArg)
}

fun stringTooLong() {
  val str1 =
      """
      Some very long string that might mess things up
      """
          .trimIndent()

  val str2 =
      """
      Some very long string that might mess things up
      """
          .trimIndent(someArg)
}
