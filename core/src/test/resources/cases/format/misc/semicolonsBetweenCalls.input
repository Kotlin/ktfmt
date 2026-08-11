fun f() {
  foo(0); { dead -> lambda }

  foo(0) ; { dead -> lambda }

  foo(0) /** a */ ; /** b */ { dead -> lambda }

  foo(0) { trailing -> lambda }; { dead -> lambda }

  foo { trailing -> lambda }; { dead -> lambda }

  val x = foo(); { dead -> lambda }

  val x = bar() && foo(); { dead -> lambda }

  // `z` has a property and a method both named `bar`
  val x = z.bar; { dead -> lambda }

  // `this` has a property and a method both named `bar`
  val x = bar; { dead -> lambda }

  // Literally any callable expression is dangerous
  val x = (if (cond) x::foo else x::bar); { dead -> lambda }

  funcCall(); { dead -> lambda }.withChained(call)
}
