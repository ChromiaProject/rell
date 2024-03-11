package com.chromia.rell.dokka

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isTrue
import com.chromia.rell.dokka.config.RellDokkaPluginConfiguration
import com.chromia.rell.dokka.model.isEntity
import com.chromia.rell.dokka.model.isFunction
import com.chromia.rell.dokka.model.isIndex
import com.chromia.rell.dokka.model.isKey
import com.chromia.rell.dokka.model.isMutable
import com.chromia.rell.dokka.model.isObject
import com.chromia.rell.dokka.model.isOperation
import com.chromia.rell.dokka.model.isQuery
import com.chromia.rell.dokka.model.isStruct
import org.jetbrains.dokka.base.testApi.testRunner.BaseAbstractTest
import org.jetbrains.dokka.base.testApi.testRunner.BaseTestBuilder
import org.jetbrains.dokka.links.Callable
import org.jetbrains.dokka.links.DRI
import org.jetbrains.dokka.links.JavaClassReference
import org.jetbrains.dokka.links.PointingToCallableParameters
import org.jetbrains.dokka.links.TypeConstructor
import org.jetbrains.dokka.links.TypeParam
import org.jetbrains.dokka.model.DEnum
import org.jetbrains.dokka.model.GenericTypeConstructor
import org.jetbrains.dokka.model.KotlinModifier
import org.jetbrains.dokka.model.KotlinVisibility
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private const val TEST_DAPP_NAME = "Test dapp"

class RellDokkaPluginTest : BaseAbstractTest() {
    private val configuration = dokkaConfiguration {
        pluginsConfigurations.add(RellDokkaPluginConfiguration(TEST_DAPP_NAME, listOf("main")).toPluginConfig())
        sourceSets {
            sourceSet {
                sourceRoots = listOf("src")
            }
        }
    }

    private fun singleFileTestInline(content: String, block: BaseTestBuilder.() -> Unit) {
        testInline("""
            |/src/main.rell
            |module;
            |$content
        """.trimIndent(), configuration, cleanupOutput = false, block = block)
    }

    @Test
    fun `rell module can be found`() {
        singleFileTestInline("") {
            documentablesTransformationStage = { m ->
                assertThat(m.name).isEqualTo(TEST_DAPP_NAME)
                assertThat(m.dri.toString()).isEqualTo(DRI("").toString())
                assertThat(m.packages.size).isEqualTo(1)
                assertThat(m.packages.first().packageName).isEqualTo("main")
                assertThat(m.packages.first().dri.toString()).isEqualTo(DRI("main").toString())
            }
        }
    }

    @Test
    fun `global constant can be found`() {
        singleFileTestInline("val my_constant = 32;") {
            documentablesTransformationStage = { m ->
                val testPackage = m.packages.find { it.packageName == "main" }
                assertNotNull(testPackage)
                val properties = testPackage.properties
                assertThat(properties.size).isEqualTo(1)
                val myConstant = properties.first()
                assertThat(myConstant.name).isEqualTo("my_constant")
                assertThat(myConstant.dri.toString()).isEqualTo(DRI("main", classNames = "my_constant").toString())
                assertThat(myConstant.visibility.values.first()).isEqualTo(KotlinVisibility.Public)
                assertThat(myConstant.modifier.values.first()).isEqualTo(KotlinModifier.Empty)
                assertTrue(myConstant.type is GenericTypeConstructor)
                assertThat((myConstant.type as GenericTypeConstructor).dri.classNames).isEqualTo("integer")
            }
        }
    }

    @Test
    fun `entity transformation`() {
        singleFileTestInline("""
            entity simple {}
            
            entity my_entity {
              key name;
              index my_value: integer;
              mutable hash: byte_array;
              simple;
              // index my_value, simple; 
            }
        """.trimIndent()) {
            documentablesTransformationStage = { m ->
                val testPackage = m.packages.find { it.packageName == "main" }
                assertNotNull(testPackage)
                val entities = testPackage.classlikes
                assertThat(entities.size).isEqualTo(2)
                val (simpleEntity, myEntity) = entities
                assertThat(simpleEntity.name).isEqualTo("simple")
                assertThat(simpleEntity.isEntity()).isTrue()
                val simpleDri = simpleEntity.dri
                assertThat(simpleDri.toString()).isEqualTo(DRI("main", "simple").toString())
                val props = myEntity.properties
                assertThat(props.size).isEqualTo(4)
                val (name, value, hash, simple) = props
                assertThat(name.name).isEqualTo("name")
                assertThat(name.dri.toString()).isEqualTo(DRI("main", "my_entity.name").toString())
                assertThat((name.type as GenericTypeConstructor).dri.classNames).isEqualTo("text")
                assertThat(name.isKey()).isTrue()
                assertThat(value.name).isEqualTo("my_value")
                assertThat(value.isIndex()).isTrue()
                assertThat(hash.name).isEqualTo("hash")
                assertThat(hash.isMutable()).isTrue()
                assertThat(simple.name).isEqualTo("simple")
                assertThat(simple.dri.toString()).isEqualTo(DRI("main", "my_entity.simple").toString())
                assertThat((simple.type as GenericTypeConstructor).dri.toString()).isEqualTo(simpleDri.toString())
            }
        }
    }

    @Test
    fun `struct transformation`() {
        singleFileTestInline("""
            struct simple {}
            
            struct my_struct {
              order: integer;
              mutable hash: byte_array;
              simple;
            }
        """.trimIndent()) {
            documentablesTransformationStage = { m ->
                val testPackage = m.packages.find { it.packageName == "main" }
                assertNotNull(testPackage)
                val structs = testPackage.classlikes
                assertThat(structs.size).isEqualTo(2)
                val (simpleStruct, myStruct) = structs
                assertThat(simpleStruct.name).isEqualTo("simple")
                assertThat(simpleStruct.isStruct()).isTrue()
                val simpleDri = simpleStruct.dri
                assertThat(simpleDri.toString()).isEqualTo(DRI("main", "simple").toString())
                val props = myStruct.properties
                assertThat(props.size).isEqualTo(3)
                val (order, hash, simple) = props
                assertThat(order.name).isEqualTo("order")
                assertThat(order.dri.toString()).isEqualTo(DRI("main", "my_struct.order").toString())
                assertThat((order.type as GenericTypeConstructor).dri.classNames).isEqualTo("integer")
                assertThat(hash.name).isEqualTo("hash")
                assertThat(hash.isMutable()).isTrue()
                assertThat(simple.name).isEqualTo("simple")
                assertThat(simple.dri.toString()).isEqualTo(DRI("main", "my_struct.simple").toString())
                assertThat((simple.type as GenericTypeConstructor).dri.toString()).isEqualTo(simpleDri.toString())
            }
        }
    }

    @Test
    fun `object transformation`() {
        singleFileTestInline("""           
            object my_object {
              order: integer = 1;
              mutable name: text = "my_object";
            }
        """.trimIndent()) {
            documentablesTransformationStage = { m ->
                val testPackage = m.packages.find { it.packageName == "main" }
                assertNotNull(testPackage)
                val objects = testPackage.classlikes
                assertThat(objects.size).isEqualTo(1)
                val myObject = objects.first()
                assertThat(myObject.name).isEqualTo("my_object")
                assertThat(myObject.isObject()).isTrue()
                assertThat(myObject.dri.toString()).isEqualTo(DRI("main", "my_object").toString())
                val props = myObject.properties
                assertThat(props.size).isEqualTo(2)
                val (order, name) = props
                assertThat(order.name).isEqualTo("order")
                assertThat(order.dri.toString()).isEqualTo(DRI("main", "my_object.order").toString())
                assertThat((order.type as GenericTypeConstructor).dri.classNames).isEqualTo("integer")
                assertThat(name.name).isEqualTo("name")
                assertThat(name.isMutable()).isTrue()
            }
        }
    }

    @Test
    fun `enum transformation`() {
        singleFileTestInline("""           
            enum my_enum {
              A, B, C
            }
        """.trimIndent()) {
            documentablesTransformationStage = { m ->
                val testPackage = m.packages.find { it.packageName == "main" }
                assertNotNull(testPackage)
                val enums = testPackage.classlikes
                assertThat(enums.size).isEqualTo(1)
                val myEnum = enums.first()
                assertThat(myEnum.name).isEqualTo("my_enum")
                assertThat(myEnum).isInstanceOf<DEnum>()
                assertThat(myEnum.dri.toString()).isEqualTo(DRI("main", "my_enum").toString())
                val entries = (myEnum as DEnum ).entries
                assertThat(entries.size).isEqualTo(3)
                val entry = entries.first()
                assertThat(entry.name).isEqualTo("A")
                assertThat(entry.dri.toString()).isEqualTo(DRI("main", "my_enum.A").toString())
            }
        }
    }

    @Test
    fun `function transformation`() {
        singleFileTestInline("""           
            function my_fun(name) = 13;
            operation my_operation(name) {}
            query my_query(name) = 13;
        """.trimIndent()) {
            documentablesTransformationStage = { m ->
                val testPackage = m.packages.find { it.packageName == "main" }
                assertNotNull(testPackage)
                val functions = testPackage.functions
                assertThat(functions.size).isEqualTo(3)
                val (myFun, myOperation, myQuery) = functions
                assertThat(myFun.isFunction()).isTrue()
                assertThat(myOperation.isOperation()).isTrue()
                assertThat(myQuery.isQuery()).isTrue()
                assertThat(myFun.name).isEqualTo("my_fun")
                val expectedDri = DRI("main", callable = Callable("my_fun", params = listOf(TypeConstructor("name", listOf(JavaClassReference("text"))))))
                assertThat(myFun.dri.toString()).isEqualTo(expectedDri.toString())
                val args = myFun.parameters
                assertThat(args.size).isEqualTo(1)
                val name = args.first()
                assertThat(name.name).isEqualTo("name")
                assertThat(name.dri.toString()).isEqualTo(expectedDri.copy(target = PointingToCallableParameters(0)).toString())
                assertThat(name.type).isInstanceOf<GenericTypeConstructor>()
                assertThat((name.type as GenericTypeConstructor).projections.size).isEqualTo(0)
            }
        }
    }

    @Test
    @Disabled
    fun `rell plugin should find packages and classes`() {
        testInline(
                """
            |/src/main.rell
            |module;
            |entity foo { name; }
            |/**
            | * My comment
            | */
            |operation my_operation(arg: text, i: integer) {}
            |function my_fun(arg: integer) = foo @{};
            """.trimIndent(), configuration, cleanupOutput = false
        ) {
            documentablesTransformationStage = { module ->
                val testedPackage = module.packages.find { it.name == "main" }
                val testedClass = testedPackage?.functions?.find { it.name == "my_operation" }

                assertNotNull(testedPackage)
                assertNotNull(testedClass)
            }
        }
    }
}