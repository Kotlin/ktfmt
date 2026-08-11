fun f() {
  return Text.create(c)
    .onTouch {
      when (it.motionEvent.action) {
        ACTION_DOWN ->
          Toast.makeText(it.view.context, "Down!", Toast.LENGTH_SHORT, blablabla).show()
        ACTION_UP -> Toast.makeText(it.view.context, "Up!", Toast.LENGTH_SHORT).show()
        ACTION_DOWN ->
          Toast.makeText(
              it.view.context,
              "Down!",
              Toast.LENGTH_SHORT,
              blablabla,
              blablabl,
              blablabl,
              blablabl,
              blabla,
            )
            .show()
      }
    }
    .build()
}
