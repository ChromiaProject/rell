package net.postchain.rell.toolbox.lsp.caching

import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory

class IndexPersisterThreadFactory : ThreadFactory {
    private val defaultFactory = Executors.defaultThreadFactory()

    override fun newThread(runnable: Runnable): Thread {
        val thread = defaultFactory.newThread(runnable)
        thread.isDaemon = true
        thread.name = "Index-Cache-Persister-${thread.threadId()}"
        return thread
    }
}
