package com.bandknife.tension.domain

/**
 * 帯刃の材種プリセット。密度とヤング率をまとめて設定する。
 */
data class BladeMaterialPreset(
    val id: String,
    val label: String,
    val densityKgM3: Double,
    val youngModulusGpa: Double
) {
    companion object {
        val CARBON_STEEL = BladeMaterialPreset(
            id = "carbon_steel",
            label = "炭素鋼",
            densityKgM3 = 7850.0,
            youngModulusGpa = 210.0
        )
        val SKS = BladeMaterialPreset(
            id = "sks",
            label = "合金工具鋼 (SKS)",
            densityKgM3 = 7850.0,
            youngModulusGpa = 210.0
        )
        val STAINLESS = BladeMaterialPreset(
            id = "stainless",
            label = "ステンレス",
            densityKgM3 = 8000.0,
            youngModulusGpa = 193.0
        )

        val ALL = listOf(CARBON_STEEL, SKS, STAINLESS)

        fun fromId(id: String): BladeMaterialPreset =
            ALL.find { it.id == id } ?: CARBON_STEEL
    }
}
