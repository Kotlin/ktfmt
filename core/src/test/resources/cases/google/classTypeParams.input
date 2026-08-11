// MAX_WIDTH 36

class Foo<
  TypeA : Int,
  TypeC : String,
> {
  // Class name + type params too
  // long for one line
  // Type params could fit on one
  // line but break
}

class Foo<
  TypeA : Int,
  TypeB : Double,
  TypeC : String,
> {
  // Type params can't fit on one
  // line
}

class Foo<
  TypeA : Int,
  TypeB : Double,
  TypeC : String,
>

class Foo<
  TypeA : Int,
  TypeB : Double,
  TypeC : String,
>() {
  //
}

class Bi<
  TypeA : Int,
  TypeB : Double,
  TypeC : String,
>(a: Int, var b: Int, val c: Int) {
  // TODO: Breaking the type param
  // list should propagate to the
  //  value param list
}

class C<A : Int, B : Int, C : Int> {
  // Class name + type params fit on
  // one line
}
