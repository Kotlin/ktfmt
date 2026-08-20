// MAX_WIDTH 60

val propertyTypeFits: @Ann String? = ""

val propertyTypeOverflows:
    @Ann("arg")
    Map<String, List<Int>> =
    mapOf()

val typeArgumentFits: List<@Ann Int> = listOf()

val typeArgumentOverflows:
    Map<@Ann String, List<@Ann Int>> =
    mapOf()

fun returnTypeFits(): @Ann String = ""

fun returnTypeOverflows(a: String): @Ann("arg") List<Int> =
    listOf()

fun paramTypeFits(a: @Ann List<Int>) {}

fun paramTypeOverflows(
    a: @Ann("arg") Map<String, List<Int>>,
    b: Int,
) {}

fun @receiver:Ann String.receiverFits() = this

fun @receiver:NotNull("arg") String.receiverOverflows(
    a: Int,
) = this

fun asExpression(x: Any) = x as @Ann String

fun isExpression(x: Any) =
    x is @Ann("arg") Map<String, List<Int>>

class SupertypeFits : @Ann Base()

class SupertypeOverflows :
    @Ann("arg") Base<String, Int>(), @Ann I

fun functionTypeFits(compact: @Composable () -> Unit) {}

fun functionTypeOverflows(
    b: (@A Int, @B String) -> @C List<Int>,
    x: Int,
) = 1

typealias Alias = @Anno () -> Unit

fun targetOnFunctionType(
    e:
        @field:[Inject Named("WEB")]
        ((Int) -> Unit),
) {}

val nestedTypeArgument:
    (@Anno List<@JvmSuppressWildcards Int>) -> Unit =
    f

class Variance<@Anno out @Anno T, @Anno in @Anno U>

inline fun <@Anno reified T> reifiedTypeParameter() {}

class ConstraintsFits<T : @Anno Kip, U> where U : @Anno Kip

class ConstraintsOverflows<T : @Anno("arg") Kip, U>
    where U : @Anno("arg") Qux<T>

fun projection(x: Foo<in @Anno Int>) {}
