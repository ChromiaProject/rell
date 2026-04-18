/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.seeder.generator.pattern.category

import net.postchain.rell.toolbox.seeder.generator.pattern.GeneratorRegistry

class CryptoGenerators(registry: GeneratorRegistry) : GeneratorCategory(registry) {
    override fun register() {
        register("crypto.md5") { selectGenerator(it, it.faker.crypto::md5, it.faker.crypto.unique::md5) }
        register("crypto.sha1") { selectGenerator(it, it.faker.crypto::sha1, it.faker.crypto.unique::sha1) }
        register("crypto.sha224") { selectGenerator(it, it.faker.crypto::sha224, it.faker.crypto.unique::sha224) }
        register("crypto.sha256") { selectGenerator(it, it.faker.crypto::sha256, it.faker.crypto.unique::sha256) }
        register("crypto.sha384") { selectGenerator(it, it.faker.crypto::sha384, it.faker.crypto.unique::sha384) }
        register("crypto.sha512") { selectGenerator(it, it.faker.crypto::sha512, it.faker.crypto.unique::sha512) }
    }
}
