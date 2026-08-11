class Foo : Bar() {
  fun doIt() {
    fruit.onlyBananas().forEach { banana ->
      val seeds = banana.find { it.type == SEED }
      println(seeds)
    }

    fruit
        .filter { isBanana(it, Bananas.types) }
        .forEach { banana ->
          val seeds = banana.find { it.type == SEED }
          println(seeds)
        }
  }
}
