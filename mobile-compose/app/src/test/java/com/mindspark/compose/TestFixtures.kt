package com.mindspark.compose

object TestFixtures {
    fun fixture(name: String): String {
        val path = if (name.startsWith("fixtures/")) name else "fixtures/$name"
        val stream = javaClass.classLoader?.getResourceAsStream(path)
            ?: throw IllegalArgumentException("Fixture resource not found: $path")
        return stream.bufferedReader().use { it.readText() }
    }
}

fun fixture(name: String): String = TestFixtures.fixture(name)
