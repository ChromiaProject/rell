package com.chromia.rell.dokka.config

import kotlinx.serialization.Serializable

@Serializable
data class RellConfig(val modules: List<String>?)
