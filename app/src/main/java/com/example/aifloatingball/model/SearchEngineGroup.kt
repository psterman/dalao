package com.example.aifloatingball.model

import java.io.Serializable

data class SearchEngineGroup(
    var id: Long,
    var name: String,
    var engines: MutableList<SearchEngine>,
    var isEnabled: Boolean
) : Serializable 