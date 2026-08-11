// MAX_WIDTH 50

override fun visitProperty(property: KtProperty) {
  builder.sync(property)
  builder.block(ZERO) {
    declareOne(
      kind = DeclarationKind.FIELD,
      modifiers = property.modifierList,
      valOrVarKeyword =
        property.valOrVarKeyword.text,
      typeParametersBlaBla =
        property.typeParameterList,
      receiver = property.receiverTypeReference,
      name = property.nameIdentifier?.text,
      type = property.typeReference,
      typeConstraintList =
        property.typeConstraintList,
      delegate = property.delegate,
      initializer = property.initializer,
    )
  }
}
