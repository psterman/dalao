data class SearchEngine(
    val name: String,
    val url: String,
    var order: Int,
    var enabled: Boolean = true
) 