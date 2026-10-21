package io.github.kawinnext.revanced.googlephotos.misc.backup

import io.github.kawinnext.morphe.Fingerprint

internal object isDCIMFolderBackupControlMethod : Fingerprint(
    strings = listOf(
        "/dcim",
        "/mars_files/"
    ),
    parameters = listOf("Ljava/lang/String;"),
    returnType = "Z"
)