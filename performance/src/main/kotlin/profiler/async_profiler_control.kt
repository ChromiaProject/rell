/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */
package net.postchain.rell.performance.profiler

import com.sun.tools.attach.VirtualMachine
import com.sun.tools.attach.VirtualMachineDescriptor
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.createDirectories
import kotlin.io.path.isRegularFile

/**
 * Out-of-process control of async-profiler in another JVM via the HotSpot Attach API.
 * Each `loadAgentPath` call sends one command (start/dump/stop) to the libasyncProfiler agent.
 */
internal class AsyncProfilerControl(
    private val pid: String,
    private val agentPath: Path,
) : AutoCloseable {

    private var vm: VirtualMachine? = null

    private fun vm(): VirtualMachine = vm ?: VirtualMachine.attach(pid).also { vm = it }

    fun start(event: String, jfrFile: Path) {
        jfrFile.parent.createDirectories()
        val args = "start,event=$event,total,jfr,file=${jfrFile.absolutePathString()}"
        vm().loadAgentPath(agentPath.absolutePathString(), args)
    }

    /** Dump the current capture in [format] (collapsed | flamegraph | jfr) to [file]. */
    fun dump(format: String, file: Path) {
        file.parent.createDirectories()
        vm().loadAgentPath(
            agentPath.toAbsolutePath().toString(),
            "dump,$format,file=${file.toAbsolutePath()}",
        )
    }

    fun stop() {
        vm().loadAgentPath(agentPath.toAbsolutePath().toString(), "stop")
    }

    override fun close() {
        try {
            vm?.detach()
        } catch (_: Exception) {
        } finally {
            vm = null
        }
    }
}

internal fun asprofAgentPath(): Path? {
    val base = perfDir().resolve("async-profiler").resolve("lib")
    val candidates = listOf("libasyncProfiler.dylib", "libasyncProfiler.so")
    for (name in candidates) {
        val p = base.resolve(name)
        if (p.isRegularFile()) return p
    }
    return null
}

internal fun findJvmPid(keywords: List<String>): String? {
    val selfPid = ProcessHandle.current().pid().toString()
    val descriptors: List<VirtualMachineDescriptor> = try {
        VirtualMachine.list()
    } catch (_: Throwable) {
        return null
    }
    for (d in descriptors) {
        if (d.id() == selfPid) continue
        val display = d.displayName().lowercase()
        if (keywords.any { it.lowercase() in display }) return d.id()
    }
    return null
}
