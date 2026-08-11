// MAX_WIDTH 79

class Foo : Bar() {
  fun doIt() {
    // don't break in lambda, no argument breaks found
    fruit.forEach { eat(it) }

    // break in lambda, natural break
    fruit.forEach(
      someVeryLongParameterNameThatWillCauseABreak,
      evenWithoutATrailingCommaOnTheParameterListSoLetsSeeIt,
    ) {
      eat(it)
    }

    // break in the lambda, forced break
    fruit.forEach(
      fromTheVine = true, //
    ) {
      eat(it)
    }

    // don't break in the inner lambda, as nesting doesn't respect outer levels
    fruit.forEach(
      fromTheVine = true, //
    ) {
      fruit.forEach { eat(it) }
    }

    // don't break in the lambda, as breaks don't propagate
    fruit
      .onlyBananas(
        fromTheVine = true, //
      )
      .forEach { eat(it) }

    // don't break in the inner lambda, as breaks don't propagate to parameters
    fruit.onlyBananas(
      fromTheVine = true,
      processThem = { eat(it) }, //
    ) {
      eat(it)
    }

    // don't break in the inner lambda, as breaks don't propagate to the body
    fruit.onlyBananas(
      fromTheVine = true, //
    ) {
      val anon = { eat(it) }
    }
  }
}
