package dev.injun.scalelite

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable data object Home : NavKey

@Serializable data object AddDevice : NavKey

@Serializable data object Diagnostics : NavKey
