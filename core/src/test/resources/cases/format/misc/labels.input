fun f(a: List<Int>) {
  a.map {
    myloop@ for (i in a) {
      if (true) {
        break@myloop
      } else if (false) {
        continue@myloop
      } else {
        a.map `inner map`@{
          return@`inner map`
        }
      }
    }
    return@map 2 * it
  }
}
