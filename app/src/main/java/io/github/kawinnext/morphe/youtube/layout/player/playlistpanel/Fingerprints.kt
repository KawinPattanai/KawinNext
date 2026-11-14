package io.github.kawinnext.morphe.youtube.layout.player.playlistpanel

import io.github.kawinnext.morphe.AccessFlags
import io.github.kawinnext.morphe.Fingerprint
import io.github.kawinnext.morphe.InstructionLocation.MatchAfterImmediately
import io.github.kawinnext.morphe.InstructionLocation.MatchAfterWithin
import io.github.kawinnext.morphe.Opcode
import io.github.kawinnext.morphe.anyInstruction
import io.github.kawinnext.morphe.fieldAccess
import io.github.kawinnext.morphe.findClassDirect
import io.github.kawinnext.morphe.findMethodListDirect
import io.github.kawinnext.morphe.literal
import io.github.kawinnext.morphe.methodCall
import io.github.kawinnext.morphe.opcode
import io.github.kawinnext.morphe.string
import org.luckypray.dexkit.result.FieldData
import org.luckypray.dexkit.result.MethodData

/**
 * YouTube's rule whether a panel opened in fullscreen exits fullscreen (true on any tablet).
 */
internal object PanelExitsFullscreenFingerprint : Fingerprint(
    returnType = "Z",
    filters = listOf(
        string("[EngagementPanel] Cannot cannot determine if shouldExitFullscreen because EngagementPanelController has not been initialized.")
    )
)

/**
 * The enum of the modes a panel opens in: beside the player, or over the fullscreen player.
 */
internal object PanelModeEnumFingerprint : Fingerprint(
    name = "<clinit>",
    strings = listOf("PORTRAIT_WATCH_PANEL", "FULLSCREEN_PLAYER_OVERLAY")
)

/**
 * The enum class of those modes, which the rule where a panel opens reads.
 */
internal val panelModeEnumClass = findClassDirect {
    PanelModeEnumFingerprint.run().declaredClass!!
}

/**
 * YouTube's rule where a panel opens. Its first check is the server config that enables the panel
 * overlay over the fullscreen player, then the check for the fullscreen state. Depending on the app
 * build the config is read inline (a boolean field) or by a static call, so either read is matched.
 * Some versions have two variants of this method, chosen by a server flag.
 */
internal fun getPanelModeFingerprint(panelModeType: String) = Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "Ljava/lang/Object;",
    filters = listOf(
        anyInstruction(
            opcode(Opcode.MOVE_RESULT),
            fieldAccess(opcode = Opcode.IGET_BOOLEAN)
        ),
        anyInstruction(
            opcode(Opcode.IF_EQZ),
            opcode(Opcode.IF_NEZ),
            location = MatchAfterImmediately()
        ),
        // The fullscreen state.
        literal(3, location = MatchAfterWithin(10)),
        opcode(Opcode.IF_EQ, location = MatchAfterImmediately()),
        // The panel beside the player.
        fieldAccess(
            opcode = Opcode.SGET_OBJECT,
            type = panelModeType,
            location = MatchAfterImmediately()
        )
    )
)

/**
 * YouTube's rule whether a panel is refused for the current mode. Over the fullscreen player it
 * refuses most panels unless a server config allows them (the boolean read after the config call).
 */
internal object PanelRefusedFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC),
    returnType = "Z",
    parameters = listOf("L", "L", "L", "L", "L"),
    filters = listOf(
        literal(45673598L),
        methodCall(opcode = Opcode.INVOKE_STATIC, parameters = listOf("L"), returnType = "L"),
        opcode(Opcode.MOVE_RESULT_OBJECT, location = MatchAfterImmediately()),
        fieldAccess(opcode = Opcode.IGET_BOOLEAN, location = MatchAfterImmediately())
    )
)

/**
 * Where a panel rule takes the server config it reads from.
 */
internal sealed interface PanelConfigRead {
    /** The rule reads the boolean a call returns. */
    class Call(val method: MethodData) : PanelConfigRead

    /**
     * The rule reads a boolean field of an object it gets from a call: the object itself, or the
     * object held by [container].
     */
    class FieldOfCallResult(val method: MethodData, val container: FieldData?) : PanelConfigRead

    /** The rule reads a boolean field of the static default instance of the config class. */
    class StaticInstance(val instance: FieldData) : PanelConfigRead
}

/**
 * The server config one panel rule reads, and the boolean field it reads it from.
 * The field is null for a rule that reads the value from the result of a call.
 */
internal class PanelConfigPlan(
    val rule: MethodData,
    val field: FieldData?,
    val reads: List<PanelConfigRead>
)

/**
 * The server config reads of the panel rules, in the order of [panelConfigRules].
 *
 * A finder hands over methods only, so resolving [panelConfigRules] fills this list, and the patch
 * reads it right after its finder.
 */
internal var panelConfigPlans: List<PanelConfigPlan> = emptyList()
    private set

/** The panel rules: where a panel opens, and where a panel is refused for the current mode. */
internal val panelConfigRules = findMethodListDirect {
    panelConfigPlans = buildList {
        getPanelModeFingerprint(panelModeEnumClass().descriptor).matchAllOrNull().orEmpty()
            .forEach { match ->
                add(match.method.panelConfigPlan(match.instructionMatches.first().index))
            }

        PanelRefusedFingerprint.matchAllOrNull().orEmpty().forEach { match ->
            add(match.method.panelConfigPlan(match.instructionMatches.last().index))
        }
    }
    panelConfigPlans.map { it.rule }
}

/**
 * How the config read at [index] is reached. The read is the boolean a call returns, or a boolean
 * field of the object the rule holds the config in.
 */
private fun MethodData.panelConfigPlan(index: Int): PanelConfigPlan {
    val instructions = instructions.toList()
    val read = instructions[index]

    if (Opcode.fromInt(read.opcode) == Opcode.MOVE_RESULT) {
        val call = instructions[index - 1].methodRef
            ?: throw IllegalStateException("No call returns the config of $this")
        return PanelConfigPlan(this, null, listOf(PanelConfigRead.Call(call)))
    }

    val field = read.fieldRef ?: throw IllegalStateException("No field holds the config of $this")
    val configType = field.declaredClass!!.descriptor
    val register = read.registerOrNull(1)
        ?: throw IllegalStateException("No object holds the config of $this at $index")

    // Every place the object of the read can come from: a branch may take the static default of the
    // config class, and another may read it from the object the rule got from a call.
    val reads = mutableListOf<PanelConfigRead>()
    instructions.forEachIndexed { candidateIndex, candidate ->
        if (candidateIndex >= index) return@forEachIndexed
        if (candidate.registerOrNull(0) != register) return@forEachIndexed

        when (Opcode.fromInt(candidate.opcode)) {
            Opcode.SGET_OBJECT -> candidate.fieldRef?.let {
                if (it.type.descriptor == configType) reads += PanelConfigRead.StaticInstance(it)
            }

            Opcode.MOVE_RESULT_OBJECT -> {
                val call = instructions.getOrNull(candidateIndex - 1)?.methodRef
                    ?: return@forEachIndexed
                if (call.returnType?.descriptor == configType) {
                    reads += PanelConfigRead.FieldOfCallResult(call, null)
                }
            }

            Opcode.IGET_OBJECT -> {
                // The object of the read is a field of another object, which a call returns.
                val container = candidate.fieldRef ?: return@forEachIndexed
                if (container.type.descriptor != configType) return@forEachIndexed
                val source = candidate.registerOrNull(1) ?: return@forEachIndexed
                for (back in candidateIndex - 1 downTo 0) {
                    val defining = instructions[back]
                    if (defining.registerOrNull(0) != source) continue
                    if (Opcode.fromInt(defining.opcode) == Opcode.MOVE_RESULT_OBJECT) {
                        instructions.getOrNull(back - 1)?.methodRef?.let {
                            reads += PanelConfigRead.FieldOfCallResult(it, container)
                        }
                    }
                    break
                }
            }

            else -> Unit
        }
    }

    return PanelConfigPlan(this, field, reads)
}
