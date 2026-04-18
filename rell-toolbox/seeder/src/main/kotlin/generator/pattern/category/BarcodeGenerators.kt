/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.seeder.generator.pattern.category

import net.postchain.rell.toolbox.seeder.generator.pattern.GeneratorRegistry

class BarcodeGenerators(registry: GeneratorRegistry) : GeneratorCategory(registry) {
    override fun register() {
        register("barcode.ean8") { selectGenerator(it, it.faker.barcode::ean8, it.faker.barcode.unique::ean8) }
        register("barcode.ean13") { selectGenerator(it, it.faker.barcode::ean13, it.faker.barcode.unique::ean13) }
        register("barcode.upca") { selectGenerator(it, it.faker.barcode::upcA, it.faker.barcode.unique::upcA) }
        register("barcode.upce") { selectGenerator(it, it.faker.barcode::upcE, it.faker.barcode.unique::upcE) }
        register("barcode.composite_symbol") {
            selectGenerator(
                it,
                it.faker.barcode::compositeSymbol,
                it.faker.barcode.unique::compositeSymbol
            )
        }
        register("barcode.isbn") { selectGenerator(it, it.faker.barcode::isbn, it.faker.barcode.unique::isbn) }
        register("barcode.ismn") { selectGenerator(it, it.faker.barcode::ismn, it.faker.barcode.unique::ismn) }
        register("barcode.issn") { selectGenerator(it, it.faker.barcode::issn, it.faker.barcode.unique::issn) }
    }
}
