/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.toolbox.seeder.generator.pattern.category

import net.postchain.rell.toolbox.seeder.generator.pattern.GeneratorRegistry

class EduGenerators(registry: GeneratorRegistry) : GeneratorCategory(registry) {
    override fun register() {
        // Educator
        register("educator.school_name") {
            selectGenerator(it, it.faker.educator::schoolName, it.faker.educator.unique::schoolName)
        }
        register("educator.secondary") {
            selectGenerator(it, it.faker.educator::secondary, it.faker.educator.unique::secondary)
        }
        register(
            "educator.primary"
        ) { selectGenerator(it, it.faker.educator::primary, it.faker.educator.unique::primary) }
        register("educator.secondary_school") {
            selectGenerator(it, it.faker.educator::secondarySchool, it.faker.educator.unique::secondarySchool)
        }
        register("educator.primary_school") {
            selectGenerator(it, it.faker.educator::primarySchool, it.faker.educator.unique::primarySchool)
        }
        register("educator.campus") { selectGenerator(it, it.faker.educator::campus, it.faker.educator.unique::campus) }
        register(
            "educator.subject"
        ) { selectGenerator(it, it.faker.educator::subject, it.faker.educator.unique::subject) }
        register("educator.university_type") {
            selectGenerator(
                it,
                it.faker.educator.tertiary::universityType,
                it.faker.educator.unique.tertiary::universityType
            )
        }
        register("educator.degree_type") {
            selectGenerator(it, it.faker.educator.tertiary.degree::type, it.faker.educator.unique.tertiary.degree::type)
        }
        register("educator.course_number") {
            selectGenerator(
                it,
                it.faker.educator.tertiary.degree::courseNumber,
                it.faker.educator.unique.tertiary.degree::courseNumber
            )
        }

        // Job
        register("job.field") {
            selectGenerator(it, it.faker.job::field, it.faker.job.unique::field)
        }
        register("job.seniority") {
            selectGenerator(it, it.faker.job::seniority, it.faker.job.unique::seniority)
        }
        register("job.position") {
            selectGenerator(it, it.faker.job::position, it.faker.job.unique::position)
        }
        register("job.key_skills") {
            selectGenerator(it, it.faker.job::keySkills, it.faker.job.unique::keySkills)
        }
        register("job.employment_type") {
            selectGenerator(it, it.faker.job::employmentType, it.faker.job.unique::employmentType)
        }
        register("job.education_level") {
            selectGenerator(it, it.faker.job::educationLevel, it.faker.job.unique::educationLevel)
        }
        register("job.title") {
            selectGenerator(it, it.faker.job::title, it.faker.job.unique::title)
        }

        // Science
        register("science.element") {
            selectGenerator(it, it.faker.science::element, it.faker.science.unique::element)
        }
        register("science.element_symbol") {
            selectGenerator(it, it.faker.science::elementSymbol, it.faker.science.unique::elementSymbol)
        }
        register("science.element_state") {
            selectGenerator(it, it.faker.science::elementState, it.faker.science.unique::elementState)
        }
        register("science.element_subcategory") {
            selectGenerator(it, it.faker.science::elementSubcategory, it.faker.science.unique::elementSubcategory)
        }
        register("science.modifier") {
            selectGenerator(it, it.faker.science::modifier, it.faker.science.unique::modifier)
        }
        register("science.scientist") {
            selectGenerator(it, it.faker.science::scientist, it.faker.science.unique::scientist)
        }
        register("science.tool") {
            selectGenerator(it, it.faker.science::tool, it.faker.science.unique::tool)
        }

        // University
        register("university.prefix") {
            selectGenerator(it, it.faker.university::prefix, it.faker.university.unique::prefix)
        }
        register("university.suffix") {
            selectGenerator(it, it.faker.university::suffix, it.faker.university.unique::suffix)
        }
        register("university.name") {
            selectGenerator(it, it.faker.university::name, it.faker.university.unique::name)
        }
    }
}
