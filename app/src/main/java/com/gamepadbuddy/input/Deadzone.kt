package com.gamepadbuddy.input

/**
 * Áp dụng deadzone cho trục analog (file 02 - Bước 3).
 * Giá trị tuyệt đối nhỏ hơn ngưỡng sẽ bị kéo về 0 để tránh rung do trôi tâm.
 */
fun applyDeadzone(value: Float, threshold: Float = 0.15f): Float =
    if (kotlin.math.abs(value) < threshold) 0f else value
