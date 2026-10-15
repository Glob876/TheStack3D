package com.stackgame.core

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** The desktop game's overlap and perfect-placement rules, independent of rendering. */
object TowerRules {
    data class Placement(val start: Float, val size: Float, val perfect: Boolean)

    fun place(previousStart: Float, previousSize: Float, movingStart: Float, movingSize: Float): Placement? {
        if (abs(movingStart - previousStart) < 0.15f) {
            return Placement(previousStart, previousSize, true)
        }
        val start = max(previousStart, movingStart)
        val size = min(previousStart + previousSize, movingStart + movingSize) - start
        return if (size > 0f) Placement(start, size, false) else null
    }
}
