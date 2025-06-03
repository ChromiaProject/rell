/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.testutils

import org.junit.After

abstract class BaseResourcefulTest {
    private val resources = mutableListOf<AutoCloseable>()

    @After fun after() {
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
