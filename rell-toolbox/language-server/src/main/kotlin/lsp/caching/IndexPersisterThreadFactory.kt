/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.lsp.caching

import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory

class IndexPersisterThreadFactory : ThreadFactory {
    private val defaultFactory = Executors.defaultThreadFactory()

    override fun newThread(runnable: Runnable): Thread = defaultFactory.newThread(runnable).apply<Thread> {
        this.isDaemon = true
        this.name = "Index-Cache-Persister-${threadId()}"
    }
}
