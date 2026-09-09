// MAX_WIDTH 28

val foo = coroutineScope {
  foo()
  //
}

fun foo() = coroutineScope {
  foo()
  //
}

fun foo() = use { x ->
  foo()
  //
}

fun foo() = scope label@{
  foo()
  //
}

fun foo() =
    coroutineScope { x ->
      foo()
      //
    }

fun foo() =
    coroutineScope label@{
      foo()
      //
    }

fun foo() =
    Runnable @Px {
      foo()
      //
    }

fun longName() =
    coroutineScope {
      foo()
      //
    }
