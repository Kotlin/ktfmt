// MAX_WIDTH 50
// TRAILING_COMMA_STRATEGY NONE

override fun visitProperty(property: KtProperty) {
  builder.sync(property)
  builder.block(ZERO) {
    declareOne(
        kind = DeclarationKind.FIELD,
        modifiers = property.modifierList,
        valOrVarKeyword =
            property.valOrVarKeyword.text,
        typeParameters =
            property.typeParameterList,
        receiver = property.receiverTypeReference,
        name = property.nameIdentifier?.text,
        type = property.typeReference,
        typeConstraintList =
            property.typeConstraintList,
        delegate = property.delegate,
        initializer = property.initializer)
  }
}
