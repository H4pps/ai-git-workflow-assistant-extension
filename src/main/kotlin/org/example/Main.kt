package org.example

private const val FIRST_INDEX = 1
private const val LAST_INDEX = 5

fun main() {
    val name = "Kotlin"

    println("Hello, $name!")

    for (i in FIRST_INDEX..LAST_INDEX) {
        println("i = $i")
    }
}
