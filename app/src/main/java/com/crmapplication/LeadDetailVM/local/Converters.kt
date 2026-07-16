package com.crmapplication.LeadDetailVM.local

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun fromLabels(labels: List<String>?): String = labels.orEmpty().joinToString("\n")

    @TypeConverter
    fun toLabels(value: String?): List<String> =
        value?.takeIf { it.isNotEmpty() }?.split("\n") ?: emptyList()
}
