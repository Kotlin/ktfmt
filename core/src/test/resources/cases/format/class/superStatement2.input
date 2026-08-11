class Foo : Bar(), FooBar {
  override fun doIt() {
    foo.doThat {
      super<FooBar>@Foo.doIt()

      // this one is actually generics on the call expression, not super
      super@Foo<FooBar>.doIt()
    }
  }
}
