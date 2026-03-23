/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package com.chromia.rell.dokka.systemlib

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import net.postchain.rell.base.lib.Lib_Rell
import net.postchain.rell.base.lmodel.L_NamespaceMember_Function
import net.postchain.rell.base.model.R_QualifiedName
import org.jetbrains.dokka.DokkaSourceSetID
import org.jetbrains.dokka.DokkaSourceSetImpl
import org.jetbrains.dokka.links.DRI
import org.jetbrains.dokka.model.GenericTypeConstructor
import org.jetbrains.dokka.testApi.logger.TestLogger
import org.jetbrains.dokka.utilities.DokkaConsoleLogger
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class SystemLibFunctionDocumentableTest {
    val testSourceSet = DokkaSourceSetImpl("test", DokkaSourceSetID("testScope", "test"))
    val visitor = SystemLibVisitor(testSourceSet, TestLogger(DokkaConsoleLogger()))
    @Test
    fun test() {
        val f = Lib_Rell.MODULE.lModule.namespace.getDef(R_QualifiedName.of("crypto.privkey_to_pubkey")) as L_NamespaceMember_Function
        with (visitor) {
            val d = f.visit(DRI("crypto"))
            assertThat(d.isConstructor).isFalse()
            assertThat(d.parameters.size).isEqualTo(2)
            assertThat((d.type as GenericTypeConstructor).dri).isEqualTo(DRI("", "byte_array"))
        }
    }
}