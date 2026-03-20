package net.postchain.rell.toolbox.seeder.generator.pattern.category

import net.postchain.rell.toolbox.seeder.generator.pattern.GeneratorRegistry

class FileGenerators(registry: GeneratorRegistry) : GeneratorCategory(registry) {
    override fun register() {
        register("file.extension") { selectGenerator(it, it.faker.file::extension, it.faker.file.unique::extension) }
        register("file.mimetype_application") {
            selectGenerator(
                it,
                it.faker.file.mimeType::application,
                it.faker.file.mimeType.unique::application
            )
        }
        register("file.mimetype_audio") {
            selectGenerator(
                it,
                it.faker.file.mimeType::audio,
                it.faker.file.mimeType.unique::audio
            )
        }
        register("file.mimetype_image") {
            selectGenerator(
                it,
                it.faker.file.mimeType::image,
                it.faker.file.mimeType.unique::image
            )
        }
        register("file.mimetype_message") {
            selectGenerator(
                it,
                it.faker.file.mimeType::message,
                it.faker.file.mimeType.unique::message
            )
        }
        register("file.mimetype_model") {
            selectGenerator(
                it,
                it.faker.file.mimeType::model,
                it.faker.file.mimeType.unique::model
            )
        }
        register("file.mimetype_multipart") {
            selectGenerator(
                it,
                it.faker.file.mimeType::multipart,
                it.faker.file.mimeType.unique::multipart
            )
        }
        register("file.mimetype_text") {
            selectGenerator(
                it,
                it.faker.file.mimeType::text,
                it.faker.file.mimeType.unique::text
            )
        }
        register("file.mimetype_video") {
            selectGenerator(
                it,
                it.faker.file.mimeType::video,
                it.faker.file.mimeType.unique::video
            )
        }
    }
}
