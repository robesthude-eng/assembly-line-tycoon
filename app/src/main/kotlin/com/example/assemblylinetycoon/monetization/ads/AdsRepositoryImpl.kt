package com.example.assemblylinetycoon.monetization.ads

import com.example.assemblylinetycoon.domain.model.AdPlacement
import com.example.assemblylinetycoon.domain.model.AdResult
import com.example.assemblylinetycoon.domain.repository.AdsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Мост между доменным [AdsRepository] и менеджерами конкретной рекламной сети.
 *
 * Здесь же будут жить кулдауны плейсментов и правило «закрыл ролик — кулдаун
 * не потрачен». Домен об этом не знает и знать не должен.
 */
class AdsRepositoryImpl(
    private val rewarded: RewardedAdsManager,
    private val interstitial: InterstitialAdsManager,
) : AdsRepository {

    override fun observeAvailability(placement: AdPlacement): Flow<Boolean> =
        if (placement.rewarded) rewarded.observeAvailability() else interstitial.observeAvailability()

    override suspend fun preload(placement: AdPlacement) {
        // TODO(этап 5): выбрать unitId по плейсменту и предзагрузить объявление
    }

    override suspend fun show(placement: AdPlacement): AdResult =
        AdResult.Failed("Показ требует Activity — будет подключён вместе с SDK")
}
