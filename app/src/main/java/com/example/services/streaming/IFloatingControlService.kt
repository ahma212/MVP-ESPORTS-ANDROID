package com.example.services.streaming

import kotlinx.coroutines.flow.StateFlow

/**
 * Floating widget visual configurations and display status.
 */
data class FloatingControlState(
    val isVisible: Boolean = false,
    val isExpanded: Boolean = false,
    val pointerSize: String = "MEDIUM", // SMALL, MEDIUM, LARGE
    val lastPositionX: Int = 0,
    val lastPositionY: Int = 0
)

/**
 * Platform-independent abstraction for the floating overlay controller.
 */
interface IFloatingControlService {
    val controlState: StateFlow<FloatingControlState>

    fun showPointer()
    fun hidePointer()
    fun setExpanded(expanded: Boolean)
    fun setPointerSize(size: String)
    fun updatePosition(x: Int, y: Int)
}
