fun f() {
  builder.block(ZERO) {
    builder.token("when")
    expression1.let { subjectExp ->
      builder.token(")")
      return
    }
  }
  builder.block(ZERO) {
    expression2.subjectExpression.let { subjectExp ->
      builder.token(")")
      return
    }
  }
  builder.block(ZERO) {
    expression2.subjectExpression
        .let { subjectExp ->
          builder.token(")")
          return
        }
        .sum
  }
}
