/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.parser

import assertk.Assert
import assertk.assertions.support.expected
import assertk.assertions.support.show
import net.postchain.rell.base.compiler.ast.S_Pos
import net.postchain.rell.base.compiler.ast.S_RellFile
import java.lang.reflect.Field

fun Assert<S_RellFile>.isSimilarTo(expected: S_RellFile) = given { actual ->
    val areEqual = SRellFileComparator().compare(actual, expected) == 0
    if (areEqual) {
        return
    }
    expected("S_RellFile ${show(expected)} to be similar to ${show(actual)}")
}

class SRellFileComparator : Comparator<S_RellFile> {

    override fun compare(first: S_RellFile?, second: S_RellFile?): Int {
        val seen = mutableSetOf<Any>()
        return if (deepCompare(first, second, seen)) {
            0
        } else {
            1
        }
    }

    @Suppress("CyclomaticComplexMethod", "ReturnCount")
    private fun deepCompare(first: Any?, second: Any?, seen: MutableSet<Any>): Boolean {
        if (first == null && second == null) {
            return true
        }

        if (first == null || second == null) {
            return false
        }

        if (seen.contains(first) && seen.contains(second)) {
            return true
        }

        seen.add(first)
        seen.add(second)

        if (first is Iterable<*> && second is Iterable<*>) {
            return compareIterables(first, second, seen)
        }

        if (first is Map<*, *> && second is Map<*, *>) {
            return compareMaps(first, second, seen)
        }

        if (first.javaClass != second.javaClass) {
            // ANTLR parser uses AntlrPos class and compiler parser S_BasicPos. comparing logically
            if (first is S_Pos && second is S_Pos) {
                // Comparing only line numbers and there is difference how ANTLR and compiler parser count column
                // positions for strings containing tabs.
                // For ANTLR tab is 1 character, for compiler it's 4, resulting in mismatch.
                // Positions are used in error messages and ANTLR version is technically correct.
                // Might need to be revisited if it's used in other context as well
                return first.line() == second.line()
            }
            return false
        }

        if (first is String || first is Number || first is Boolean) {
            return first == second
        }

        val fields: Array<Field> = first.javaClass.declaredFields
        for (field in fields) {
            if (shouldBeSkipped(field)) {
                continue
            }

            field.isAccessible = true

            val value1: Any? = field.get(first)
            val value2: Any? = field.get(second)

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
        val firstList = first.toList()
        val secondList = second.toList()
        if (firstList.size != secondList.size) {
            return false
        }
        firstList.forEachIndexed { index, element ->
            if (!deepCompare(element, secondList[index], seen)) {
                return false
            }
        }
        return true
    }
}
