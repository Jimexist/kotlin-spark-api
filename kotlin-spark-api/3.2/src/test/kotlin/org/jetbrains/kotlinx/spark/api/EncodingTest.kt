/*-
 * =LICENSE=
 * Kotlin Spark API: API for Spark 3.2+ (Scala 2.12)
 * ----------
 * Copyright (C) 2019 - 2022 JetBrains
 * ----------
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =LICENSEEND=
 */
package org.jetbrains.kotlinx.spark.api

import ch.tutteli.atrium.api.fluent.en_GB.*
import ch.tutteli.atrium.api.verbs.expect
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import org.apache.spark.sql.Dataset
import org.apache.spark.sql.types.Decimal
import org.apache.spark.unsafe.types.CalendarInterval
import scala.Product
import scala.Tuple1
import scala.Tuple2
import scala.Tuple3
import java.math.BigDecimal
import java.sql.Date
import java.sql.Timestamp
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.Period

class EncodingTest : ShouldSpec({

    context("encoders") {
        withSpark(props = mapOf("spark.sql.codegen.comments" to true)) {

            should("handle LocalDate Datasets") {
                val dates = listOf(LocalDate.now(), LocalDate.now())
                val dataset: Dataset<LocalDate> = dates.toDS()
                dataset.collectAsList() shouldBe dates
            }

            should("handle Instant Datasets") {
                val instants = listOf(Instant.now(), Instant.now())
                val dataset: Dataset<Instant> = instants.toDS()
                dataset.collectAsList() shouldBe instants
            }

            should("handle Timestamp Datasets") {
                val timeStamps = listOf(Timestamp(0L), Timestamp(1L))
                val dataset = timeStamps.toDS()
                dataset.collectAsList() shouldBe timeStamps
            }

            should("handle Duration Datasets") {
                val dataset = dsOf(Duration.ZERO)
                dataset.collectAsList() shouldBe listOf(Duration.ZERO)
            }

            should("handle Period Datasets") {
                val periods = listOf(Period.ZERO, Period.ofDays(2))
                val dataset = periods.toDS()

                dataset.show(false)

                dataset.collectAsList().let {
                    it[0] shouldBe Period.ZERO

                    // NOTE Spark truncates java.time.Period to months.
                    it[1] shouldBe Period.ofDays(0)
                }
            }

            should("handle binary datasets") {
                val byteArray = "Hello there".encodeToByteArray()
                val dataset = dsOf(byteArray)
                dataset.collectAsList() shouldBe listOf(byteArray)
            }

            should("handle BigDecimal datasets") {
                val decimals = listOf(BigDecimal.ONE, BigDecimal.TEN)
                val dataset = decimals.toDS()
                dataset.collectAsList().let { (one, ten) ->
                    one.compareTo(BigDecimal.ONE) shouldBe 0
                    ten.compareTo(BigDecimal.TEN) shouldBe 0
                }
            }

            should("handle nullable datasets") {
                val ints = listOf(1, 2, 3, null)
                val dataset = ints.toDS()
                dataset.collectAsList() shouldBe ints
            }
        }
    }
    context("known dataTypes") {
        withSpark(props = mapOf("spark.sql.codegen.comments" to true)) {

            should("be able to serialize Instant") {
                val instantPair = Instant.now() to Instant.now()
                val dataset = dsOf(instantPair)
                dataset.collectAsList() shouldBe listOf(instantPair)
            }

            should("be able to serialize Date") {
                val datePair = Date.valueOf("2020-02-10") to 5
                val dataset: Dataset<Pair<Date, Int>> = dsOf(datePair)
                dataset.collectAsList() shouldBe listOf(datePair)
            }

            should("be able to serialize Timestamp") {
                val timestampPair = Timestamp(0L) to 2
                val dataset = dsOf(timestampPair)
                dataset.collectAsList() shouldBe listOf(timestampPair)
            }

            should("be able to serialize binary") {
                val byteArrayTriple = c("Hello there".encodeToByteArray(), 1, intArrayOf(1, 2, 3))
                val dataset = dsOf(byteArrayTriple)

                val (a, b, c) = dataset.collectAsList().single()
                a contentEquals "Hello there".encodeToByteArray() shouldBe true
                b shouldBe 1
                c contentEquals intArrayOf(1, 2, 3) shouldBe true
            }

            should("be able to serialize Decimal") {
                val decimalPair = c(Decimal().set(50), 12)
                val dataset = dsOf(decimalPair)
                dataset.collectAsList() shouldBe listOf(decimalPair)
            }

            should("be able to serialize BigDecimal") {
                val decimalPair = c(BigDecimal.TEN, 12)
                val dataset = dsOf(decimalPair)
                val (a, b) = dataset.collectAsList().single()
                a.compareTo(BigDecimal.TEN) shouldBe 0
                b shouldBe 12
            }

            should("be able to serialize CalendarInterval") {
                val calendarIntervalPair = CalendarInterval(1, 0, 0L) to 2
                val dataset = dsOf(calendarIntervalPair)
                dataset.collectAsList() shouldBe listOf(calendarIntervalPair)
            }

            should("Be able to serialize Scala Tuples including data classes") {
                val dataset = dsOf(
                    Tuple2("a", Tuple3("a", 1, LonLat(1.0, 1.0))),
                    Tuple2("b", Tuple3("b", 2, LonLat(1.0, 2.0))),
                )
                dataset.show()
                val asList = dataset.takeAsList(2)
                asList.first() shouldBe Tuple2("a", Tuple3("a", 1, LonLat(1.0, 1.0)))
            }

            should("Be able to serialize data classes with tuples") {
                val dataset = dsOf(
                    DataClassWithTuple(Tuple3(5L, "test", Tuple1(""))),
                    DataClassWithTuple(Tuple3(6L, "tessst", Tuple1(""))),
                )

                dataset.show()
                val asList = dataset.takeAsList(2)
                asList.first().tuple shouldBe Tuple3(5L, "test", Tuple1(""))
            }
        }
    }

    context("schema") {
        withSpark(props = mapOf("spark.sql.codegen.comments" to true)) {

            should("collect data classes with doubles correctly") {
                val ll1 = LonLat(1.0, 2.0)
                val ll2 = LonLat(3.0, 4.0)
                val lonlats = dsOf(ll1, ll2).collectAsList()
                expect(lonlats).contains.inAnyOrder.only.values(ll1.copy(), ll2.copy())
            }

            should("contain all generic primitives with complex schema") {
                val primitives = c(1, 1.0, 1.toFloat(), 1.toByte(), LocalDate.now(), true)
                val primitives2 = c(2, 2.0, 2.toFloat(), 2.toByte(), LocalDate.now().plusDays(1), false)
                val tuples = dsOf(primitives, primitives2).collectAsList()
                expect(tuples).contains.inAnyOrder.only.values(primitives, primitives2)
            }

            should("contain all generic primitives with complex nullable schema") {
                val primitives = c(1, 1.0, 1.toFloat(), 1.toByte(), LocalDate.now(), true)
                val nulls = c(null, null, null, null, null, null)
                val tuples = dsOf(primitives, nulls).collectAsList()
                expect(tuples).contains.inAnyOrder.only.values(primitives, nulls)
            }

            should("Be able to serialize lists of data classes") {
                val dataset = dsOf(
                    listOf(SomeClass(intArrayOf(1, 2, 3), 4)),
                    listOf(SomeClass(intArrayOf(3, 2, 1), 0)),
                )

                val (first, second) = dataset.collectAsList()

                first.single().let { (a, b) ->
                    a.contentEquals(intArrayOf(1, 2, 3)) shouldBe true
                    b shouldBe 4
                }
                second.single().let { (a, b) ->
                    a.contentEquals(intArrayOf(3, 2, 1)) shouldBe true
                    b shouldBe 0
                }
            }

            should("Be able to serialize arrays of data classes") {
                val dataset = dsOf(
                    arrayOf(SomeClass(intArrayOf(1, 2, 3), 4)),
                    arrayOf(SomeClass(intArrayOf(3, 2, 1), 0)),
                )

                val (first, second) = dataset.collectAsList()

                first.single().let { (a, b) ->
                    a.contentEquals(intArrayOf(1, 2, 3)) shouldBe true
                    b shouldBe 4
                }
                second.single().let { (a, b) ->
                    a.contentEquals(intArrayOf(3, 2, 1)) shouldBe true
                    b shouldBe 0
                }
            }

            should("Be able to serialize lists of tuples") {
                val dataset = dsOf(
                    listOf(Tuple2(intArrayOf(1, 2, 3), 4)),
                    listOf(Tuple2(intArrayOf(3, 2, 1), 0)),
                )

                val (first, second) = dataset.collectAsList()

                first.single().let {
                    it._1().contentEquals(intArrayOf(1, 2, 3)) shouldBe true
                    it._2() shouldBe 4
                }
                second.single().let {
                    it._1().contentEquals(intArrayOf(3, 2, 1)) shouldBe true
                    it._2() shouldBe 0
                }
            }

            should("Generate encoder correctly with complex enum data class") {
                val dataset: Dataset<ComplexEnumDataClass> =
                    dsOf(
                        ComplexEnumDataClass(
                            int = 1,
                            string = "string",
                            strings = listOf("1", "2"),
                            someEnum = SomeEnum.A,
                            someOtherEnum = SomeOtherEnum.C,
                            someEnums = listOf(SomeEnum.A, SomeEnum.B),
                            someOtherEnums = listOf(SomeOtherEnum.C, SomeOtherEnum.D),
                            someEnumArray = arrayOf(SomeEnum.A, SomeEnum.B),
                            someOtherArray = arrayOf(SomeOtherEnum.C, SomeOtherEnum.D),
                            enumMap = mapOf(SomeEnum.A to SomeOtherEnum.C),
                        )
                    )

                dataset.show(false)
                val first = dataset.takeAsList(1).first()

                first.int shouldBe 1
                first.string shouldBe "string"
                first.strings shouldBe listOf("1", "2")
                first.someEnum shouldBe SomeEnum.A
                first.someOtherEnum shouldBe SomeOtherEnum.C
                first.someEnums shouldBe listOf(SomeEnum.A, SomeEnum.B)
                first.someOtherEnums shouldBe listOf(SomeOtherEnum.C, SomeOtherEnum.D)
                first.someEnumArray shouldBe arrayOf(SomeEnum.A, SomeEnum.B)
                first.someOtherArray shouldBe arrayOf(SomeOtherEnum.C, SomeOtherEnum.D)
                first.enumMap shouldBe mapOf(SomeEnum.A to SomeOtherEnum.C)
            }

            should("work with lists of maps") {
                val result = dsOf(
                    listOf(mapOf("a" to "b", "x" to "y")),
                    listOf(mapOf("a" to "b", "x" to "y")),
                    listOf(mapOf("a" to "b", "x" to "y"))
                )
                    .showDS()
                    .map { it.last() }
                    .map { it["x"] }
                    .filterNotNull()
                    .distinct()
                    .collectAsList()
                expect(result).contains.inOrder.only.value("y")
            }

            should("work with lists of lists") {
                val result = dsOf(
                    listOf(listOf(1, 2, 3)),
                    listOf(listOf(1, 2, 3)),
                    listOf(listOf(1, 2, 3))
                )
                    .map { it.last() }
                    .map { it.first() }
                    .reduceK { a, b -> a + b }
                expect(result).toBe(3)
            }

            should("Generate schema correctly with nullalble list and map") {
                val schema = encoder<NullFieldAbleDataClass>().schema()
                schema.fields().forEach {
                    it.nullable() shouldBe true
                }
            }

            should("handle strings converted to lists") {
                data class Movie(val id: Long, val genres: String)
                data class MovieExpanded(val id: Long, val genres: List<String>)

                val comedies = listOf(Movie(1, "Comedy|Romance"), Movie(2, "Horror|Action")).toDS()
                    .map { MovieExpanded(it.id, it.genres.split("|").toList()) }
                    .filter { it.genres.contains("Comedy") }
                    .collectAsList()
                expect(comedies).contains.inAnyOrder.only.values(
                    MovieExpanded(
                        1,
                        listOf("Comedy", "Romance")
                    )
                )
            }

            should("handle strings converted to arrays") {

                data class Movie(val id: Long, val genres: String)

                data class MovieExpanded(val id: Long, val genres: Array<String>) {
                    override fun equals(other: Any?): Boolean {
                        if (this === other) return true
                        if (javaClass != other?.javaClass) return false
                        other as MovieExpanded
                        return if (id != other.id) false else genres.contentEquals(other.genres)
                    }

                    override fun hashCode(): Int {
                        var result = id.hashCode()
                        result = 31 * result + genres.contentHashCode()
                        return result
                    }
                }

                val comedies = listOf(Movie(1, "Comedy|Romance"), Movie(2, "Horror|Action")).toDS()
                    .map { MovieExpanded(it.id, it.genres.split("|").toTypedArray()) }
                    .filter { it.genres.contains("Comedy") }
                    .collectAsList()

                expect(comedies).contains.inAnyOrder.only.values(
                    MovieExpanded(
                        1,
                        arrayOf("Comedy", "Romance")
                    )
                )
            }

            should("handle arrays of generics") {
                data class Test<Z>(val id: Long, val data: Array<Pair<Z, Int>>)

                val result = listOf(Test(1, arrayOf(5.1 to 6, 6.1 to 7)))
                    .toDS()
                    .map { it.id to it.data.firstOrNull { liEl -> liEl.first < 6 } }
                    .map { it.second }
                    .collectAsList()
                expect(result).contains.inOrder.only.values(5.1 to 6)
            }

            should("handle lists of generics") {
                data class Test<Z>(val id: Long, val data: List<Pair<Z, Int>>)

                val result = listOf(Test(1, listOf(5.1 to 6, 6.1 to 7)))
                    .toDS()
                    .map { it.id to it.data.firstOrNull { liEl -> liEl.first < 6 } }
                    .map { it.second }
                    .collectAsList()
                expect(result).contains.inOrder.only.values(5.1 to 6)
            }

            should("!handle primitive arrays") {
                val result = listOf(arrayOf(1, 2, 3, 4))
                    .toDS()
                    .map { it.map { ai -> ai + 1 } }
                    .collectAsList()
                    .flatten()
                expect(result).contains.inOrder.only.values(2, 3, 4, 5)
            }
        }
    }
})

data class DataClassWithTuple<T : Product>(val tuple: T)

data class LonLat(val lon: Double, val lat: Double)

enum class SomeEnum { A, B }

enum class SomeOtherEnum(val value: Int) { C(1), D(2) }

data class ComplexEnumDataClass(
    val int: Int,
    val string: String,
    val strings: List<String>,
    val someEnum: SomeEnum,
    val someOtherEnum: SomeOtherEnum,
    val someEnums: List<SomeEnum>,
    val someOtherEnums: List<SomeOtherEnum>,
    val someEnumArray: Array<SomeEnum>,
    val someOtherArray: Array<SomeOtherEnum>,
    val enumMap: Map<SomeEnum, SomeOtherEnum>,
)

data class NullFieldAbleDataClass(
    val optionList: List<Int>?,
    val optionMap: Map<String, Int>?,
)
