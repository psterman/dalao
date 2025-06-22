package com.example.aifloatingball.settings

import android.graphics.drawable.Drawable

data class AppItem(
    val name: String,
    val packageName: String,
    val icon: Drawable,
    var isSelected: Boolean
) 