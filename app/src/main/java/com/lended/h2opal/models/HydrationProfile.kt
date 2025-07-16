package com.lended.h2opal.models

data class HydrationProfile(
    val weight: String,
    val age: String,
    val gender: String,
    val activityLevel: String,
    val wakeTime: String,
    val sleepTime: String
)

data class HydrationData(
    val userId: String,
    val totalWater: Double,
    val lastHydration: Long,
    val nextHydration: Long,
    val history: List<Pair<Long, Double>>,
)


data class RemainderModel(val quote: String, val level: String, val time: String)

data class OnboardingItem(
    val imageResId: Int,
    val title: String,
    val description: String
)
