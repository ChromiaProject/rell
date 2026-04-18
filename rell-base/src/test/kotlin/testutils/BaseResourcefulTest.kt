/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.testutils

import kotlin.test.AfterTest

abstract class BaseResourcefulTest: AutoCloseable {
    private val resources = mutableListOf<AutoCloseable>()

    @AfterTest fun after() {
        close()
    }

    override fun close() {
        for (resource in resources) {
            try {
                resource.close()
            } catch (t: Throwable) {
                t.printStackTrace()
            }
        }
    }

    protected fun <T: AutoCloseable> resource(resource: T): T {
        resources.add(resource)
        return resource
    }
}
