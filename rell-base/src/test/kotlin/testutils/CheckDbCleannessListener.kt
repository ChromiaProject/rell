/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.testutils

import org.junit.platform.launcher.LauncherSession
import org.junit.platform.launcher.LauncherSessionListener
import java.util.concurrent.atomic.AtomicBoolean

internal class CheckDbCleannessListener : LauncherSessionListener {
    override fun launcherSessionClosed(session: LauncherSession) {
        if (!enabled.get()) return

        SqlTestUtils.createSimpleConnection().use { con ->
            val schemas = SqlSchemaUtils.getAllTestSchemaNames(con)
            check(schemas.isEmpty()) {
                "Temporary test schemas $schemas were not dropped, meaning connections were leaked"
            }
        }
    }

    companion object {
        val enabled = AtomicBoolean(false)
    }
}
