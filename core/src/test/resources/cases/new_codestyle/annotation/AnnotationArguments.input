// MAX_WIDTH 55

@Foo(a = [1, 2]) fun literalFits() {}

@Foo(values = [someItem, andAnother, oneMore]) fun literalOverflows() {}

@Anno(
    array =
        [
            // Comment
            someItem,
            andAnother,
        ]
)
class LiteralWithComment

@TypeParceler<UUID, UUIDParceler>() class TypeArgsFits

@TypeParceler<Map<String, List<Int>>, Parceler>() class TypeArgsOverflows
