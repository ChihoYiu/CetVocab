package com.cetvocab.app.domain.model

/**
 * Which vocabulary list the learner is working on.
 *
 * [OTHER] exists for user-imported packs so that importing a personal list never has to
 * pretend to be an official CET list.
 */
enum class CetLevel(val id: String, val label: String, val shortLabel: String) {
    CET4("CET4", "大学英语四级", "四级"),
    CET6("CET6", "大学英语六级", "六级"),
    OTHER("OTHER", "自定义词库", "自定义");

    companion object {
        fun fromId(id: String?): CetLevel =
            entries.firstOrNull { it.id.equals(id?.trim(), ignoreCase = true) } ?: OTHER

        /** Levels offered in onboarding / settings (OTHER is import-only). */
        val selectable: List<CetLevel> = listOf(CET4, CET6)
    }
}
