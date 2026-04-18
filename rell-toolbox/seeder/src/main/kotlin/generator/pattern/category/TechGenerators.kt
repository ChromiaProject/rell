/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.seeder.generator.pattern.category

import net.postchain.rell.toolbox.seeder.generator.pattern.GeneratorRegistry

class TechGenerators(registry: GeneratorRegistry) : GeneratorCategory(registry) {
    override fun register() {
        // App
        register("app.name") { selectGenerator(it, it.faker.app::name, it.faker.app.unique::name) }
        register("app.version") { selectGenerator(it, it.faker.app::version, it.faker.app.unique::version) }
        register("app.author") { selectGenerator(it, it.faker.app::author, it.faker.app.unique::author) }

        // Appliance
        register("appliance.brand") {
            selectGenerator(it, it.faker.appliance::brand, it.faker.appliance.unique::brand)
        }
        register("appliance.equipment") {
            selectGenerator(it, it.faker.appliance::equipment, it.faker.appliance.unique::equipment)
        }

        // Camera
        register("camera.brand") { selectGenerator(it, it.faker.camera::brand, it.faker.camera.unique::brand) }
        register("camera.model") { selectGenerator(it, it.faker.camera::model, it.faker.camera.unique::model) }
        register("camera.brand_with_model") {
            selectGenerator(it, it.faker.camera::brandWithModel, it.faker.camera.unique::brandWithModel)
        }

        // Computer
        register("computer.type") { selectGenerator(it, it.faker.computer::type, it.faker.computer.unique::type) }
        register(
            "computer.platform"
        ) { selectGenerator(it, it.faker.computer::platform, it.faker.computer.unique::platform) }
        register(
            "computer.os.linux"
        ) { selectGenerator(it, it.faker.computer.os::linux, it.faker.computer.unique.os::linux) }
        register("computer.os.openbsd") {
            selectGenerator(it, it.faker.computer.os::openBsd, it.faker.computer.unique.os::openBsd)
        }
        register("computer.os.templeos") {
            selectGenerator(it, it.faker.computer.os::templeOS, it.faker.computer.unique.os::templeOS)
        }
        register(
            "computer.os.plan9"
        ) { selectGenerator(it, it.faker.computer.os::plan9, it.faker.computer.unique.os::plan9) }
        register(
            "computer.os.macos"
        ) { selectGenerator(it, it.faker.computer.os::macOS, it.faker.computer.unique.os::macOS) }
        register("computer.os.windows") {
            selectGenerator(it, it.faker.computer.os::windows, it.faker.computer.unique.os::windows)
        }

        // Crypto coin
        register("crypto_coin.coin") {
            selectGenerator(it, it.faker.cryptoCoin::coin, it.faker.cryptoCoin.unique::coin)
        }

        // Device
        register("device.platform") { selectGenerator(it, it.faker.device::platform, it.faker.device.unique::platform) }
        register(
            "device.model_name"
        ) { selectGenerator(it, it.faker.device::modelName, it.faker.device.unique::modelName) }
        register("device.manufacturer") {
            selectGenerator(it, it.faker.device::manufacturer, it.faker.device.unique::manufacturer)
        }
        register("device.serial") {
            selectGenerator(it, it.faker.device::serial, it.faker.device.unique::serial)
        }

        // Programming language
        register("programming_language.name") {
            selectGenerator(it, it.faker.programmingLanguage::name, it.faker.programmingLanguage.unique::name)
        }
        register("programming_language.creator") {
            selectGenerator(it, it.faker.programmingLanguage::creator, it.faker.programmingLanguage.unique::creator)
        }
    }
}
