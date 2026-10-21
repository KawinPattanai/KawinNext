package io.github.kawinnext.morphe.shared.misc.request

import io.github.kawinnext.TargetApp
import io.github.kawinnext.morphe.AccessFlags
import io.github.kawinnext.morphe.Fingerprint
import io.github.kawinnext.morphe.InstructionLocation.MatchAfterWithin
import io.github.kawinnext.morphe.Opcode
import io.github.kawinnext.morphe.StrictQuery
import io.github.kawinnext.morphe.findMethodDirect
import io.github.kawinnext.morphe.methodCall

@StrictQuery
@TargetApp("youtube")
internal object BuildRequestFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC),
    returnType = "Lorg/chromium/net/UrlRequest", // UrlRequest; or UrlRequest$Builder;
    filters = listOf(
        methodCall(
            opcode = Opcode.INVOKE_VIRTUAL,
            name = "newUrlRequestBuilder",
            parameters = listOf(
                "Ljava/lang/String;",
                "Lorg/chromium/net/UrlRequest\$Callback;",
                "Ljava/util/concurrent/Executor;"
            )
        ),
        methodCall(
            opcode = Opcode.INVOKE_INTERFACE,
            smali = "Ljava/util/Map;->entrySet()Ljava/util/Set;",
            location = MatchAfterWithin(15)
        ),
        methodCall(opcode = Opcode.INVOKE_VIRTUAL, name = "ordinal"),
        methodCall(opcode = Opcode.INVOKE_VIRTUAL, name = "setPriority")
    ),
    custom = {
        addInvoke {
            returnType {
                addMethod {
                    name = "<clinit>"
                    usingStrings(listOf("LOW", "NORMAL", "HIGH", "IMMEDIATE"))
                }
            }
        }
    }
)

@get:TargetApp("youtube")
val requestUrlMethod = findMethodDirect {
    val method = BuildRequestFingerprint()
    val builder = method.instructions.indexOfFirst { it.methodRef?.name == "newUrlRequestBuilder" }
    method.instructions.take(builder).last {
        it.methodRef?.returnType?.descriptor == "Ljava/lang/String;" &&
                it.methodRef?.declaredClass == method.paramTypes.first()
    }.methodRef!!
}
