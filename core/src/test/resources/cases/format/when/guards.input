fun feedAnimal(animal: Animal) {
    when (animal) {
        is Animal.Dog -> animal.feedDog()
        is Animal.Cat if !animal.mouseHunter -> animal.feedCat()
        is Animal.Cat           if     !animal.birdHunter -> animal.feedCat()
        is Animal.Cat if
          !animal.birdHunter -> animal.feedCat()
        is Animal.Cat if     (!animal.birdHunter) -> animal.feedCat()
          else  if animal
              .eatsPlants -> animal.giveLettuce()
        else -> println("Unknown animal")
    }
}
