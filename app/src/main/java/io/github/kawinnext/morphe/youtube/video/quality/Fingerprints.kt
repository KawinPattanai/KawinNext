package io.github.kawinnext.morphe.youtube.video.quality

import io.github.kawinnext.morphe.AccessFlags
import io.github.kawinnext.morphe.Fingerprint
import io.github.kawinnext.morphe.InstructionLocation.MatchAfterWithin
import io.github.kawinnext.morphe.Opcode
import io.github.kawinnext.morphe.ResourceType
import io.github.kawinnext.morphe.accessFlags
import io.github.kawinnext.morphe.checkCast
import io.github.kawinnext.morphe.fieldAccess
import io.github.kawinnext.morphe.findFieldDirect
import io.github.kawinnext.morphe.findFieldFromToString
import io.github.kawinnext.morphe.findMethodDirect
import io.github.kawinnext.morphe.findMethodListDirect
import io.github.kawinnext.morphe.literal
import io.github.kawinnext.morphe.methodCall
import io.github.kawinnext.morphe.opcode
import io.github.kawinnext.morphe.opcodes
import io.github.kawinnext.morphe.parameters
import io.github.kawinnext.morphe.resourceLiteral
import io.github.kawinnext.morphe.resourceMappings
import io.github.kawinnext.morphe.returns
import io.github.kawinnext.morphe.string

internal const val FIXED_RESOLUTION_STRING = ", initialPlaybackVideoQualityFixedResolution="

/**
 * 'Video quality preferences' setting of YouTube settings
 * (Auto, Higher picture quality, Data saver or Advanced).
 */
internal object VideoQualitySettingEnumFingerprint : Fingerprint(
    name = "<clinit>",
    filters = listOf(
        string("VIDEO_QUALITY_SETTING_ADVANCED_MENU"),
        fieldAccess( // VIDEO_QUALITY_SETTING_ADVANCED_MENU enum value.
            opcode = Opcode.SPUT_OBJECT,
            definingClass = "this",
            location = MatchAfterWithin(5)
        )
    )
)

val advancedVideoQualitySettingField = findFieldDirect {
    VideoQualitySettingEnumFingerprint.instructionMatches.last().instruction.fieldRef!!
}

/**
 * Video quality setting used for a video ID, sent with the SABR requests.
 */
val getVideoQualitySettingMethod = findMethodDirect {
    val type = VideoQualitySettingEnumFingerprint().declaredClass!!.descriptor
    Fingerprint(
        accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
        returnType = type,
        parameters = listOf("Ljava/lang/String;"),
        filters = listOf(
            methodCall(definingClass = "Ljava/util/Map;", name = "get"),
            checkCast(type)
        )
    )()
}

/**
 * Chooses the qualities allowed by the player, when the video starts and again while it plays:
 * the quality last selected in the quality menu, or an automatic quality.
 */
val getVideoQualityConstraintMethod = findMethodDirect {
    Fingerprint(
        accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
        parameters = listOf("Z", "L", "Ljava/lang/String;", "L"),
        filters = listOf(methodCall(reference = getVideoQualitySettingMethod()))
    ).match().method
}

val fixedVideoQualityConstraintMethod = findMethodDirect {
    val constraintType = getVideoQualityConstraintMethod().returnType!!
    constraintType.methods.single { method ->
        method.returnType == constraintType && method.paramTypes.map { it.descriptor } == listOf("I")
    }
}

internal object PlaybackStartParametersToStringFingerprint : Fingerprint(
    name = "toString",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "Ljava/lang/String;",
    parameters = listOf(),
    filters = listOf(
        string(FIXED_RESOLUTION_STRING)
    )
)

val InitialResolutionField = findFieldDirect {
    PlaybackStartParametersToStringFingerprint().findFieldFromToString(FIXED_RESOLUTION_STRING)
}

val PlaybackStartParametersInit = findMethodDirect {
    Fingerprint(
        classFingerprint = PlaybackStartParametersToStringFingerprint,
        name = "<init>",
        filters = listOf(
            fieldAccess(
                opcode = Opcode.IPUT_OBJECT,
                reference = InitialResolutionField()
            )
        )
    )()
}

private object VideoQualityItemOnClickParentFingerprint : Fingerprint(
    returnType = "V",
    filters = listOf(
        string("VIDEO_QUALITIES_MENU_BOTTOM_SHEET_FRAGMENT")
    )
)

/**
 * Resolves to class found in [VideoQualityItemOnClickFingerprint].
 */
internal object VideoQualityItemOnClickFingerprint : Fingerprint(
    classFingerprint = VideoQualityItemOnClickParentFingerprint,
    name = "onItemClick",
    returnType = "V",
    parameters = listOf(
        "Landroid/widget/AdapterView;",
        "Landroid/view/View;",
        "I",
        "J"
    )
)

val videoQualityBottomSheetListFragmentTitle
    get() = resourceMappings[
        "layout",
        "video_quality_bottom_sheet_list_fragment_title",
    ]

val videoQualityMenuViewInflateFingerprint = findMethodListDirect {
    // two matches in versions 20.43.32
    // one match in versions <=v20.42.xx
    findMethod {
        matcher {
            accessFlags(AccessFlags.PUBLIC, AccessFlags.FINAL)
            returns("L")
            parameters("L", "L", "L")
            opcodes(
                Opcode.INVOKE_SUPER,
                Opcode.CONST,
                Opcode.CONST_4,
                Opcode.INVOKE_VIRTUAL,
                Opcode.MOVE_RESULT_OBJECT,
                Opcode.CONST,
                Opcode.INVOKE_VIRTUAL,
                Opcode.MOVE_RESULT_OBJECT,
                Opcode.CONST_16,
                Opcode.INVOKE_VIRTUAL,
                Opcode.CONST,
                Opcode.INVOKE_VIRTUAL,
                Opcode.MOVE_RESULT_OBJECT,
                Opcode.CHECK_CAST,
            )
            literal { videoQualityBottomSheetListFragmentTitle }
        }
    }
}

val ShowVideoQualityQuickMenuFingerprint = findMethodListDirect {
    val matcher = Fingerprint(
        accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
        returnType = "V",
        strings = listOf("VIDEO_QUALITIES_QUICK_MENU_BOTTOM_SHEET_FRAGMENT"),
        filters = listOf(
            opcode(Opcode.MOVE_RESULT),
            opcode(
                opcode = Opcode.IF_NEZ,
                location = MatchAfterWithin(3)
            ),
            methodCall(
                opcode = Opcode.INVOKE_VIRTUAL,
                name = "getSupportFragmentManager",
                location = MatchAfterWithin(3)
            ),
            methodCall(
                opcode = Opcode.INVOKE_VIRTUAL,
                parameters = listOf("L", "Ljava/lang/String;"),
                returnType = "V",
                location = MatchAfterWithin(5)
            )
        )
    ).buildMethodMatcher()
    findMethod { matcher(matcher) }
}

internal object ShortsQualityMenuFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    parameters = listOf("Z"),
    returnType = "V",
    filters = listOf(
        resourceLiteral(
            type = ResourceType.STRING,
            name = "video_quality_unavailable_announcement"
        )
    )
)

internal object ShortsQualityConstructorFingerprint : Fingerprint(
    classFingerprint = ShortsQualityMenuFingerprint,
    name = "<init>",
    filters = listOf(
        fieldAccess(
            opcode = Opcode.IPUT_OBJECT,
            definingClass = "this"
        )
    )
)
