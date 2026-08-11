class Foo {
  companion object {
    var instance: Foo? = null

    fun getInstance() {
      return instance ?: synchronized(Foo::class) {
        Foo().also { instance = it }
      }
    }
  }
}
