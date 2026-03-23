/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.seeder.generator

import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import io.github.serpro69.kfaker.exception.RetryLimitException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertTrue

class UniqueProducerTest {

    @Test
    fun `nextUnique should return unique values`() {
        val uniqueProducer = UniqueProducer<Int>()
        val values = mutableListOf<Int>()

        repeat(5) {
            values.add(uniqueProducer.nextUnique { it + 1 })
        }

        assertThat(values).hasSize(5)
        assertThat(values).containsExactlyInAnyOrder(1, 2, 3, 4, 5)
    }

    @Test
    fun `nextUnique should retry until unique value is found`() {
        val uniqueProducer = UniqueProducer<Int>()
        val sequence = sequenceOf(1, 1, 2).iterator()

        val first = uniqueProducer.nextUnique { sequence.next() }
        assertThat(first).isEqualTo(1)

        val second = uniqueProducer.nextUnique { sequence.next() }
        assertThat(second).isEqualTo(2)
    }

    @Test
    fun `nextUnique should throw RetryLimitException after max retries`() {
        val uniqueProducer = UniqueProducer<Int>(maxRetries = 3)

        uniqueProducer.nextUnique { 1 }

        val exception = assertThrows<RetryLimitException> {
            uniqueProducer.nextUnique { 1 }
        }

        assertTrue(exception.message!!.contains("Failed to produce a unique value after 3 retries"))
    }

    @Test
    fun `reset should clear all produced values`() {
        val uniqueProducer = UniqueProducer<Int>()

        repeat(5) {
            uniqueProducer.nextUnique { it + 1 }
        }
        uniqueProducer.reset()

        val value = uniqueProducer.nextUnique { 1 }
        assertThat(value).isEqualTo(1)
    }

    @Test
    fun `different instances should maintain separate produced sets`() {
        val producer1 = UniqueProducer<String>()
        val producer2 = UniqueProducer<String>()

        producer1.nextUnique { "test" }

        val value = producer2.nextUnique { "test" }
        assertThat(value).isEqualTo("test")
    }

    @Test
    fun `should work with different types`() {
        val stringProducer = UniqueProducer<String>()
        val stringValue = stringProducer.nextUnique { "unique" }
        assertThat(stringValue).isEqualTo("unique")

        val doubleProducer = UniqueProducer<Double>()
        val doubleValue = doubleProducer.nextUnique { 3.14 }
        assertThat(doubleValue).isEqualTo(3.14)

        data class Person(val name: String, val age: Int)
        val personProducer = UniqueProducer<Person>()
        val person = personProducer.nextUnique { Person("Alice", 30) }
        assertThat(person).isEqualTo(Person("Alice", 30))
    }
}
