package net.postchain.rell.toolbox.seeder

import net.postchain.rell.base.model.R_LangVersion
import net.postchain.rell.base.utils.RellVersions
import net.postchain.rell.toolbox.seeder.config.Configuration
import net.postchain.rell.toolbox.seeder.config.InitialConfigGenerator
import net.postchain.rell.toolbox.seeder.config.parser.ConfigurationParser
import net.postchain.rell.toolbox.seeder.exporter.DataExporterFactory
import net.postchain.rell.toolbox.seeder.exporter.OutputFormat
import net.postchain.rell.toolbox.seeder.generator.DataGenerator
import java.io.File
import java.nio.file.Path

object SeederApi {
    fun generateDefaultConfigurations(params: InitialConfigurationParams) {
        val rellSchema = SchemaReader().readSchema(params.sourceDir, params.modules, params.rellVersion)
        InitialConfigGenerator().generate(rellSchema, params.outputPath)
    }

    fun generate(params: SeederParams) {
        val rellSchema = SchemaReader().readSchema(params.sourceDir, params.modules, params.rellVersion)

        val configuration = params.seederConfig?.let {
            ConfigurationParser().parseConfiguration(it, rellSchema)
        } ?: Configuration()

        val generatedData = DataGenerator().generate(rellSchema, configuration)
        val dataExporter = DataExporterFactory.createExporter(OutputFormat.RELL)

        dataExporter.export(generatedData, rellSchema, params.outputPath)
    }
}

class SeederParams(
    val sourceDir: File,
    val outputPath: Path,
    val modules: List<String>?,
    val rellVersion: R_LangVersion,
    val seederConfig: Path?
) {

    class Builder {
        private var sourceDir: File? = null
        private var outputPath: Path? = null
        private var modules: List<String>? = null
        private var rellVersion: R_LangVersion = RellVersions.VERSION
        private var seederConfig: Path? = null

        fun sourceDir(file: File) = apply { sourceDir = file }

        fun outputPath(path: Path) = apply { outputPath = path }

        fun modules(moduleList: List<String>?) = apply { modules = moduleList }

        fun rellVersion(version: String) = apply { rellVersion = R_LangVersion.of(version) }

        fun rootConfig(path: Path?) = apply { seederConfig = path }

        fun build(): SeederParams {
            requireNotNull(sourceDir) { "sourceDir must be set" }
            requireNotNull(outputPath) { "outputPath must be set" }

            return SeederParams(sourceDir!!, outputPath!!, modules, rellVersion, seederConfig)
        }
    }
}

class InitialConfigurationParams(
    val sourceDir: File,
    val outputPath: Path,
    val modules: List<String>?,
    val rellVersion: R_LangVersion
) {
    class Builder {
        private var sourceDir: File? = null
        private var outputPath: Path? = null
        private var modules: List<String>? = null
        private var rellVersion: R_LangVersion = RellVersions.VERSION

        fun sourceDir(file: File) = apply { sourceDir = file }

        fun outputPath(path: Path) = apply { outputPath = path }

        fun modules(moduleList: List<String>?) = apply { modules = moduleList }

        fun rellVersion(version: String) = apply { rellVersion = R_LangVersion.of(version) }

        fun build(): InitialConfigurationParams {
            requireNotNull(sourceDir) { "sourceDir must be set" }
            requireNotNull(outputPath) { "outputPath must be set" }

            return InitialConfigurationParams(sourceDir!!, outputPath!!, modules, rellVersion)
        }
    }
}
