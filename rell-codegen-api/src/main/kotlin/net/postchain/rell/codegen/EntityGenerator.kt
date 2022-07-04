package net.postchain.rell.codegen

import net.postchain.rell.model.R_App
import java.io.File

interface EntityGenerator {
    fun generate(app: R_App, targetFolder: File)
}