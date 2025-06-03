/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.testutils

abstract class BaseContextTest(useSql: Boolean): BaseResourcefulTest() {
    protected val tstCtx = resource(RellTestContext(projExt = getProjExt(), useSql = useSql))

    protected open fun getProjExt(): RellTestProjExt = BaseRellTestProjExt
}
