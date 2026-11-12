/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches/pull/3953
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package io.github.kawinnext.morphe.youtube.interaction.nativeplaylistqueue

import com.google.protobuf.MessageLite
import java.lang.reflect.Field
import java.lang.reflect.Method

// Upstream keeps two helpers in BridgeMethods.kt. One adds a method to a class of the app that
// returns a single field of that class (addBridgeMethod); the other builds the smali that parses a
// byte array with the protobuf runtime of the app (parseProtoInstructions). A module cannot add a
// method to a class of the app, so the members those bridge methods would read are read from the
// outside here, and a message crosses between the app and the extension as bytes only, exactly as
// the smali bridge does it.

/**
 * The value [field] holds on [instance], as the bridge method that upstream adds to the class of
 * the field returns it.
 *
 * A bridge method is declared on the class of its field, so its receiver is already of that type
 * and the smali needs no check. Here the receiver arrives as [Any], and the check-cast of the
 * bridge is the test below: a field is only read from an instance of the class that declares it,
 * which also keeps a field of a sibling class from being read with the wrong receiver.
 */
internal fun bridgeField(field: Field, instance: Any?): Any? =
    if (instance != null && field.declaringClass.isInstance(instance)) field.read(instance) else null

/**
 * The value [method] returns when called on [instance], as the bridge method that upstream adds
 * to the class which delegates to that call returns it.
 *
 * The same receiver test applies: the app method is only called on an object of its own class.
 */
internal fun bridgeCall(method: Method, instance: Any?): Any? =
    if (instance != null && method.declaringClass.isInstance(instance)) method.call(instance) else null

/**
 * The app's message as the module's own copy of the same message, which is the only way a message
 * crosses between the two protobuf runtimes.
 *
 * [parse] is the entry point of the module schema, so the bytes are read by the generated class the
 * extension uses itself, and its own fields are what the extension then sees.
 */
internal fun <T : MessageLite> bridgeMessage(message: Any?, parse: (ByteArray) -> T): T? =
    appMessageBytes(message)?.let(parse)
