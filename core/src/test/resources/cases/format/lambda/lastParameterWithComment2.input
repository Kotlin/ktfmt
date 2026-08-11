// MAX_WIDTH 40
// TRAILING_COMMA_STRATEGY NONE

private val a =
    firstCall().prop.call(
        mySuperInterestingParameter) {
          /* no-op */
        }
private val A =
    firstCall().prop.call(
        mySuperInterestingParameter) {
          TODO("...")
        }

fun b() {
  myProp.funCall(param) { /* 12345 */ }
  myProp.funCall(param) { TODO("123") }

  myProp.funCall(param) { /* 123456 */ }
  myProp.funCall(param) { TODO("1234") }

  myProp.funCall(param) {
    /* 1234567 */
  }
  myProp.funCall(param) {
    TODO("12345")
  }

  myProp.funCall(param) {
    /* 12345678 */
  }
  myProp.funCall(param) {
    TODO("123456")
  }

  myProp.funCall(param) {
    /* 123456789 */
  }
  myProp.funCall(param) {
    TODO("1234567")
  }

  myProp.funCall(param) {
    /* very_very_long_comment_that_should_go_on_its_own_line */
  }
  myProp.funCall(param) {
    TODO(
        "_a_very_long_comment_that_should_go_on_its_own_line")
  }
}

private val c =
    firstCall().prop.call(param) {
      /* no-op */
    }
private val C =
    firstCall().prop.call(param) {
      TODO("...")
    }
