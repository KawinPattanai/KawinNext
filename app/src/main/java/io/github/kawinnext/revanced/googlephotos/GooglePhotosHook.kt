package io.github.kawinnext.revanced.googlephotos

import io.github.kawinnext.revanced.googlephotos.misc.backup.EnableDCIMFoldersBackupControl
import io.github.kawinnext.revanced.googlephotos.misc.features.SpoofFeaturesPatch

val GooglePhotosPatches = arrayOf(
    SpoofFeaturesPatch,
    EnableDCIMFoldersBackupControl,
)