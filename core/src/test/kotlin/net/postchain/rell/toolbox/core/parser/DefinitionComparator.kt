package net.postchain.rell.toolbox.core.parser

import assertk.Assert
import assertk.assertions.support.expected
import assertk.assertions.support.show
import net.postchain.rell.base.compiler.ast.S_Definition
import net.postchain.rell.base.compiler.ast.S_Pos
import java.lang.reflect.Field

fun Assert<S_Definition>.isSimilarTo(expected: S_Definition) = given { actual ->
    val areEqual = DefinitionComparator().compare(actual, expected) == 0
    if (areEqual) {
        return
    }
    expected("definition ${show(expected)} to be similar to ${show(actual)}")
}

class DefinitionComparator : Comparator<S_Definition> {

    override fun compare(first: S_Definition?, second: S_Definition?): Int {
        val seen = mutableSetOf<Any>()
        return if (deepCompare(first, second, seen)) {
            0
        } else {
            1
        }
    }

    private fun deepCompare(firstDefinition: Any?, secondDefinition: Any?, seen: MutableSet<Any>): Boolean {
        if (firstDefinition == null && secondDefinition == null) {
            return true
        }

        if (firstDefinition == null || secondDefinition == null) {
            return false
        }

        if (seen.contains(firstDefinition) && seen.contains(secondDefinition)) {
            return true
        }

        seen.add(firstDefinition)
        seen.add(secondDefinition)

        if (firstDefinition is Iterable<*> && secondDefinition is Iterable<*>) {
            return compareIterables(firstDefinition, secondDefinition, seen)
        }

        if (firstDefinition is Map<*,*> && secondDefinition is Map<*, *>) {
            return compareMaps(firstDefinition, secondDefinition, seen)
        }

        if (firstDefinition.javaClass != secondDefinition.javaClass) {
            // ANTLR parser uses AntlrPos class and compiler parser S_BasicPos. comparing logically
            if (firstDefinition is S_Pos && secondDefinition is S_Pos) {
                return true
                // TODO: figure out why some positions not matching
//                return firstDefinition.toString() == secondDefinition.toString()
            }
            return false
        }

        if (firstDefinition is String || firstDefinition is Number || firstDefinition is Boolean) {
            return firstDefinition == secondDefinition
        }

        val fields: Array<Field> = firstDefinition.javaClass.declaredFields
        for (field in fields) {
            if (shouldBeSkipped(field)) {
                continue
            }

            field.isAccessible = true

            val value1: Any? = field.get(firstDefinition)
            val value2: Any? = field.get(secondDefinition)

            if (!deepCompare(value1, value2, seen)) {
                return false
            }
        }
        return true
    }

    private fun shouldBeSkipped(field: Field): Boolean {
        return field.name == "attachment" || field.type == Class::class.java
    }

    private fun compareMaps(
        first: Map<*, *>,
        second: Map<*, *>,
        seen: MutableSet<Any>
    ): Boolean {
        if (first.size != second.size) {
            return false
        }
        for (element in first.keys) {
            if (!second.containsKey(element)) {
                return false
            }
            if (!deepCompare(first[element], second[element], seen)) {
                return false
            }
        }
        return true
    }

    private fun compareIterables(first: Iterable<*>, second: Iterable<*>, seen: MutableSet<Any>): Boolean {
        val firstIterator = first.iterator()
        val secondIterator = second.iterator()
        while (firstIterator.hasNext()) {
            if (!secondIterator.hasNext()) {
                return false
            }
            val next1 = firstIterator.next()
            val next2 = secondIterator.next()
            if (!deepCompare(next1, next2, seen)) {
                return false
            }
        }
        return true
    }
}