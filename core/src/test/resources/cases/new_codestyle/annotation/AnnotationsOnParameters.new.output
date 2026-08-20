private class AnnotatedConstructor(
    @JvmField @Volatile private var state: Int,
    @get:JvmName("ctx") val context: CoroutineContext,
    @Deprecated("Use context instead", ReplaceWith("context")) val legacyContext: CoroutineContext,
)

private class AnnotatedParameters {
    fun twoAnnotationsFits(@NotNull @Size(min = 1) a: String, @Nullable b: String?) = a + b

    fun twoAnnotationsOverflows(
        @Suppress("UNUSED_PARAMETER")
        @Size(min = 1, max = 100, message = "out of range")
        someLongParameterName: String,
    ) = 1

    fun wrapped(
        @NotNull @Size(min = 1, max = 100) someVeryLongParameterName: String,
        @Nullable anotherRatherLongParameterName: String? = null,
    ) = someVeryLongParameterName + anotherRatherLongParameterName

    fun varargs(@NotNull vararg values: String) = values.size
}
