package io.github.kawinnext.morphe.youtube.misc.litho.node

import io.github.kawinnext.morphe.shared.misc.litho.context.conversionContextPatch
import io.github.kawinnext.morphe.shared.misc.litho.node.createTreeNodeElementHookPatch
import io.github.kawinnext.patch

val TreeNodeElementHook = createTreeNodeElementHookPatch(
    patch {},
    conversionContextPatch,
    false,
    useLegacyContextRegister = { false }
)