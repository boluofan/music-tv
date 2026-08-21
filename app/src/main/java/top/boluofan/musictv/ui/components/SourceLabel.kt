package top.boluofan.musictv.ui.components

/** 平台 code 对应的中文显示名（发现/搜索/首页统一使用，避免名称不一致） */
fun sourceLabel(source: String?): String = when (source) {
    "kw" -> "小蜗"
    "kg" -> "小枸"
    "tx" -> "小秋"
    "wy" -> "小芸"
    "mg" -> "小蜜"
    else -> source ?: ""
}
