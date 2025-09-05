package com.example.aifloatingball.model

import android.graphics.drawable.Drawable
 
data class AppInfo(
    val label: String,
    val packageName: String,
    val icon: Drawable,
    val urlScheme: String? = null
) 