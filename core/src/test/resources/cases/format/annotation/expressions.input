// MAX_WIDTH 48

fun f() {
  @Suppress("MagicNumber") add(10) && add(20)

  @Suppress("MagicNumber")
  add(10) && add(20)

  @Anno1 @Anno2(param = Param1::class)
  add(10) && add(20)

  @Anno1
  @Anno2(param = Param1::class)
  @Anno3
  @Anno4(param = Param2::class)
  add(10) && add(20)

  @Anno1
  @Anno2(param = Param1::class)
  @Anno3
  @Anno4(param = Param2::class)
  add(10) && add(20)

  @Suppress("MagicNumber") add(10) &&
      add(20) &&
      add(30)

  add(@Suppress("MagicNumber") 10) &&
      add(20) &&
      add(30)
}
