package com.example.assemblylinetycoon.domain.usecase

import com.example.assemblylinetycoon.domain.model.AdPlacement
import com.example.assemblylinetycoon.domain.model.AdResult
import com.example.assemblylinetycoon.domain.repository.AdsRepository

/**
 * Показ рекламы за награду.
 *
 * Начисление самой награды сюда не входит: use case отвечает только за показ,
 * а эффект применяет игровой движок по результату [AdResult.Rewarded].
 */
class ShowRewardedAdUseCase(
    private val adsRepository: AdsRepository,
) : SuspendUseCase<AdPlacement, AdResult> {
    override suspend fun invoke(params: AdPlacement): AdResult = adsRepository.show(params)
}
