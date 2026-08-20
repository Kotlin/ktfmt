@JvmStatic val arglessOnProperty = 1

@Suppress("UNUSED_PARAMETER", "UNUSED_EXPRESSION") val withArgsOnProperty = 1

@Test fun arglessOnFunction() {}

@Target(AnnotationTarget.CLASS) annotation class OnAnnotationClass

@A private val beforeModifier = 1

private @Deprecated("some reason") val afterModifier = 1

public @Magic final class Interleaved

public @Magic(1, "argument") final class InterleavedWithArgs

class PrimaryCtor @Inject constructor(val a: Int)

class PrimaryCtorWithArgs @Inject("qualifier") internal constructor(val a: Int)

class SecondaryCtor(a: Int, b: String) {
  @Inject constructor() : this(1, "")

  @Inject("qualifier") constructor(a: Int) : this(a, "default")
}

var accessors: Int = 1
  @Anno get() = field
  @Anno set(value) {}

var multipleAccessorAnnotations: Int = 1
  @Anno("arg") get() = field + someOtherValueHere
  @Deprecated("Deprecation message") @TestOnly set

enum class EnumEntries {
  @A FOO,
  @Deprecated("Use FOO", ReplaceWith("FOO")) BAR,
}

fun localDeclarations() {
  @A val local = 1

  @Suppress("UNUSED_VARIABLE") val localWithArgs = compute(a, b)

  @A fun inner() {}
}
