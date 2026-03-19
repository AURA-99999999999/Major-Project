package com.aura.music.data.mapper

import com.aura.music.data.model.MixCardMeta
import com.aura.music.data.remote.dto.DailyMixMetaDto

fun DailyMixMetaDto.toMixCardMeta(): MixCardMeta {
    return MixCardMeta(
        key = key ?: "",
        title = name ?: "",
        subtitle = description ?: ""
    )
}

fun List<DailyMixMetaDto>.toMixCardMetaList(): List<MixCardMeta> = this.map { it.toMixCardMeta() }
