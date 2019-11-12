package com.arpadfodor.android.paw_scanner.models.api

data class BreedImage(
    val breeds: List<BreedInfo>,
    val height: Int,
    val id: String,
    val url: String,
    val width: Int
)

data class BreedInfo(
    val bred_for: String,
    val breed_group: String,
    val height: Height,
    val id: Int,
    val life_span: String,
    val name: String,
    val temperament: String,
    val weight: Weight
)

data class Height(
    val imperial: String,
    val metric: String
)

data class Weight(
    val imperial: String,
    val metric: String
)