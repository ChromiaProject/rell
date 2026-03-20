package net.postchain.rell.toolbox.seeder.generator

import io.github.serpro69.kfaker.exception.RetryLimitException

class UniqueProducer<R>(private val maxRetries: Int = 1000) {
    private val produced = mutableSetOf<R>()

    fun nextUnique(producer: () -> R): R {
        var retriesLeft = maxRetries
        var result = producer()
        if (produced.add(result)) {
            return result
        }

        while (retriesLeft > 0) {
            result = producer()
            if (produced.add(result)) {
                return result // Found a unique value
            }
            retriesLeft--
        }

        throw RetryLimitException("Failed to produce a unique value after $maxRetries retries. ")
    }

    fun reset() {
        produced.clear()
    }
}

