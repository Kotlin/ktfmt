fun quux() = runnnnn {
  foo()
  bar()
}
    .baz()

fun quux() = runnnnn {
  foo()
  bar()
}
    .baz {
      foo()
      bar()
    }

fun quux() {
  runnnnn {
    foo()
    bar()
  }
      .baz()
}

fun quux() {
  runnnnn {
    foo()
    runnnnn {
      foo()
      bar()
    }
        .baz {
          foo()
          bar()
        }
  }
      .baz {
        foo()
        runnnnn {
          foo()
          bar()
        }
            .baz {
              foo()
              bar()
            }
      }
}

val baz = runnnnn {
  foo()
  bar()
}
    .baz {
      foo()
      bar()
    }

fun quux() {
  val baz = runnnnn {
    foo()
    bar()
  }
      .baz {
        foo()
        bar()
      }
}
