package com.luno.gateway.bridge

import com.luno.gateway.model.SimInfo
import com.luno.gateway.bridge.generated.SimInfo as SimInfoDto

/**
 * Domain model ↔ Pigeon DTO mapping, kept in one place so the agent's domain
 * types never leak Pigeon dependencies and vice versa. Pigeon represents Dart
 * `int` as Kotlin `Long`, so numeric fields are widened here.
 */
fun SimInfo.toDto(): SimInfoDto = SimInfoDto(
    subscriptionId = subscriptionId.toLong(),
    slotIndex = slotIndex.toLong(),
    carrierName = carrierName,
    displayName = displayName,
    isEmbedded = isEmbedded,
    simState = simState,
)
