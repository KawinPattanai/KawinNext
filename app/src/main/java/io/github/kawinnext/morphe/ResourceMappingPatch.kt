package io.github.kawinnext.morphe

import org.luckypray.dexkit.query.matchers.MethodMatcher
import org.luckypray.dexkit.result.InstructionData
import org.luckypray.dexkit.result.MethodData

enum class ResourceType(val value: String) {
    ANIM("anim"),
    ANIMATOR("animator"),
    ARRAY("array"),
    ATTR("attr"),
    BOOL("bool"),
    COLOR("color"),
    DIMEN("dimen"),
    DRAWABLE("drawable"),
    FONT("font"),
    FRACTION("fraction"),
    ID("id"),
    INTEGER("integer"),
    INTERPOLATOR("interpolator"),
    LAYOUT("layout"),
    MENU("menu"),
    MIPMAP("mipmap"),
    NAVIGATION("navigation"),
    PLURALS("plurals"),
    RAW("raw"),
    STRING("string"),
    STYLE("style"),
    STYLEABLE("styleable"),
    TRANSITION("transition"),
    VALUES("values"),
    XML("xml");

    companion object {
        private val VALUE_MAP: Map<String, ResourceType> = entries.associateBy { it.value }

        fun fromValue(value: String) =
            VALUE_MAP[value] ?: throw IllegalArgumentException("Unknown resource type: $value")
    }
}

interface ResourceFinder {
    operator fun get(type: String, name: String): Int
}

lateinit var resourceMappings: ResourceFinder

/**
 * @return A resource id of the given resource type and name.
 * @throws PatchException if the resource is not found.
 */
fun getResourceId(type: ResourceType, name: String) = resourceMappings[type.value, name]


/**
 * @return If the resource exists.
 */
fun hasResourceId(type: ResourceType, name: String) =
    runCatching { resourceMappings[type.value, name] }.isSuccess


class ResourceLiteralFilter(
    private val type: ResourceType,
    private val name: String,
    private val exceptionIfResourceNotFound: Boolean = true,
    location: InstructionLocation
) : OpcodesFilter(null as List<Opcode>?, location) {

    /**
     * The id is looked up every time it is read, and is deliberately not cached in this filter.
     *
     * A fingerprint is a singleton, so its filters are shared by every APK the same run
     * examines, and the id of one resource differs between APK versions. A cached id
     * would resolve against the first APK and then fail against the next one.
     */
    private val literalValue: Long? get() {
        return if (exceptionIfResourceNotFound || hasResourceId(type, name)) {
            getResourceId(type, name).toLong()
        } else {
            null
        }
    }

    override fun matches(
        enclosingMethod: MethodData,
        instruction: InstructionData
    ): Boolean {
        if (!super.matches(enclosingMethod, instruction)) {
            return false
        }

        if (instruction.literal == null) return false

        // Decoding a resource id is not free (on device it goes through Resources),
        // so resolve it once for this instruction instead of once per read.
        val literal = literalValue ?: return false

        return instruction.literal == literal
    }

    context(matcher: MethodMatcher)
    override fun addQuery() {
        literalValue?.let { matcher.addUsingNumber(it) }
    }
}

/**
 * Identical to [LiteralFilter] except uses a decoded resource literal value.
 *
 * Any patch with fingerprints of this filter must
 * also declare [resourceMappingPatch] as a dependency.
 *
 * @param exceptionIfResourceNotFound If false and the resource does not exist,
 *   then this filter effectively never matches anything. This should only be used
 *   with [app.morphe.patcher.anyInstruction] where one of the resource filters
 *   may not exist in all app versions.
 */
fun resourceLiteral(
    type: ResourceType,
    name: String,
    exceptionIfResourceNotFound: Boolean = true,
    location: InstructionLocation = InstructionLocation.MatchAfterAnywhere()
) = ResourceLiteralFilter(type, name, exceptionIfResourceNotFound, location)
