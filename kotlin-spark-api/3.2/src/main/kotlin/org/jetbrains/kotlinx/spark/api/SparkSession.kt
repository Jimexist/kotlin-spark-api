/*-
 * =LICENSE=
 * Kotlin Spark API
 * ----------
 * Copyright (C) 2019 - 2020 JetBrains
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

/**
 * This file contains the main entry points and wrappers for the Kotlin Spark API.
 */

package org.jetbrains.kotlinx.spark.api

import org.apache.spark.SparkConf
import org.apache.spark.SparkContext
import org.apache.spark.api.java.JavaRDDLike
import org.apache.spark.api.java.JavaSparkContext
import org.apache.spark.broadcast.Broadcast
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.Dataset
import org.apache.spark.sql.SparkSession.Builder
import org.apache.spark.sql.UDFRegistration
import org.jetbrains.kotlinx.spark.api.SparkLogLevel.ERROR
import org.jetbrains.kotlinx.spark.extensions.KSparkExtensions

/**
 * This wrapper over [SparkSession] which provides several additional methods to create [org.apache.spark.sql.Dataset].
 *
 *  @param spark The current [SparkSession] to wrap
 */
class KSparkSession(val spark: SparkSession) {

    /** Lazy instance of [JavaSparkContext] wrapper around [sparkContext]. */
    val sc: JavaSparkContext by lazy { JavaSparkContext(spark.sparkContext) }

    /** Utility method to create dataset from list. */
    inline fun <reified T> List<T>.toDS(): Dataset<T> = toDS(spark)

    /** Utility method to create dataset from [Array]. */
    inline fun <reified T> Array<T>.toDS(): Dataset<T> = spark.dsOf(*this)

    /** Utility method to create dataset from vararg arguments. */
    inline fun <reified T> dsOf(vararg arg: T): Dataset<T> = spark.dsOf(*arg)

    /** Utility method to create dataset from Scala [RDD]. */
    inline fun <reified T> RDD<T>.toDS(): Dataset<T> = toDS(spark)

    /** Utility method to create dataset from [JavaRDDLike]. */
    inline fun <reified T> JavaRDDLike<T, *>.toDS(): Dataset<T> = toDS(spark)

    /**
     * A collection of methods for registering user-defined functions (UDF).
     *
     * The following example registers a UDF in Kotlin:
     * ```Kotlin
     *   sparkSession.udf.register("myUDF") { arg1: Int, arg2: String -> arg2 + arg1 }
     * ```
     *
     * @note The user-defined functions must be deterministic. Due to optimization,
     * duplicate invocations may be eliminated or the function may even be invoked more times than
     * it is present in the query.
     */
    val udf: UDFRegistration get() = spark.udf()
}

/**
 * The entry point to programming Spark with the Dataset and DataFrame API.
 *
 * @see org.apache.spark.sql.SparkSession
 */
typealias SparkSession = org.apache.spark.sql.SparkSession

/**
 * Control our logLevel. This overrides any user-defined log settings.
 * @param level The desired log level as [SparkLogLevel].
 */
fun SparkContext.setLogLevel(level: SparkLogLevel): Unit = setLogLevel(level.name)

/** Log levels for spark. */
enum class SparkLogLevel {
    ALL, DEBUG, ERROR, FATAL, INFO, OFF, TRACE, WARN
}

/**
 * Returns the Spark context associated with this Spark session.
 */
val SparkSession.sparkContext: SparkContext
    get() = KSparkExtensions.sparkContext(this)

/**
 * Wrapper for spark creation which allows setting different spark params.
 *
 * @param props spark options, value types are runtime-checked for type-correctness
 * @param master Sets the Spark master URL to connect to, such as "local" to run locally, "local[4]" to
 *  run locally with 4 cores, or "spark://master:7077" to run on a Spark standalone cluster. By default, it
 *  tries to get the system value "spark.master", otherwise it uses "local[*]"
 * @param appName Sets a name for the application, which will be shown in the Spark web UI.
 *  If no application name is set, a randomly generated name will be used.
 * @param logLevel Control our logLevel. This overrides any user-defined log settings.
 * @param func function which will be executed in context of [KSparkSession] (it means that `this` inside block will point to [KSparkSession])
 */
@JvmOverloads
inline fun withSpark(
    props: Map<String, Any> = emptyMap(),
    master: String = SparkConf().get("spark.master", "local[*]"),
    appName: String = "Kotlin Spark Sample",
    logLevel: SparkLogLevel = ERROR,
    func: KSparkSession.() -> Unit,
) {
    val builder = SparkSession
        .builder()
        .master(master)
        .appName(appName)
        .apply {
            props.forEach {
                when (val value = it.value) {
                    is String -> config(it.key, value)
                    is Boolean -> config(it.key, value)
                    is Long -> config(it.key, value)
                    is Double -> config(it.key, value)
                    else -> throw IllegalArgumentException("Cannot set property ${it.key} because value $value of unsupported type ${value::class}")
                }
            }
        }
    withSpark(builder, logLevel, func)

}

/**
 * Wrapper for spark creation which allows setting different spark params.
 *
 * @param builder A [SparkSession.Builder] object, configured how you want.
 * @param logLevel Control our logLevel. This overrides any user-defined log settings.
 * @param func function which will be executed in context of [KSparkSession] (it means that `this` inside block will point to [KSparkSession])
 */
@JvmOverloads
inline fun withSpark(builder: Builder, logLevel: SparkLogLevel = ERROR, func: KSparkSession.() -> Unit) {
    builder
        .getOrCreate()
        .apply {
            KSparkSession(this).apply {
                sparkContext.setLogLevel(logLevel)
                func()
                spark.stop()
            }
        }
}

/**
 * Wrapper for spark creation which copies params from [sparkConf].
 *
 * @param sparkConf Sets a list of config options based on this.
 * @param logLevel Control our logLevel. This overrides any user-defined log settings.
 * @param func function which will be executed in context of [KSparkSession] (it means that `this` inside block will point to [KSparkSession])
 */
@JvmOverloads
inline fun withSpark(sparkConf: SparkConf, logLevel: SparkLogLevel = ERROR, func: KSparkSession.() -> Unit) {
    withSpark(
        builder = SparkSession.builder().config(sparkConf),
        logLevel = logLevel,
        func = func,
    )
}

/**
 * Broadcast a read-only variable to the cluster, returning a
 * [org.apache.spark.broadcast.Broadcast] object for reading it in distributed functions.
 * The variable will be sent to each cluster only once.
 *
 * @param value value to broadcast to the Spark nodes
 * @return `Broadcast` object, a read-only variable cached on each machine
 */
inline fun <reified T> SparkSession.broadcast(value: T): Broadcast<T> = try {
    sparkContext.broadcast(value, encoder<T>().clsTag())
} catch (e: ClassNotFoundException) {
    JavaSparkContext(sparkContext).broadcast(value)
}

/**
 * Broadcast a read-only variable to the cluster, returning a
 * [org.apache.spark.broadcast.Broadcast] object for reading it in distributed functions.
 * The variable will be sent to each cluster only once.
 *
 * @param value value to broadcast to the Spark nodes
 * @return `Broadcast` object, a read-only variable cached on each machine
 * @see broadcast
 */
@Deprecated(
    "You can now use `spark.broadcast()` instead.",
    ReplaceWith("spark.broadcast(value)"),
    DeprecationLevel.WARNING
)
inline fun <reified T> SparkContext.broadcast(value: T): Broadcast<T> = try {
    broadcast(value, encoder<T>().clsTag())
} catch (e: ClassNotFoundException) {
    JavaSparkContext(this).broadcast(value)
}

