package com.woojik.aircallai

import android.content.Context
import com.woojik.aircallai.ai.cloud.CloudAIProvider
import com.woojik.aircallai.ai.cloud.HttpCloudApiAdapter
import com.woojik.aircallai.ai.local.LocalAIProvider
import com.woojik.aircallai.ai.local.NoopLocalModelAdapter
import com.woojik.aircallai.ai.provider.AIProvider
import com.woojik.aircallai.ai.provider.ProviderRouter
import com.woojik.aircallai.core.security.AndroidKeystoreCrypto
import com.woojik.aircallai.core.storage.AppStorage
import com.woojik.aircallai.core.storage.FileCredentialManager
import com.woojik.aircallai.settings.SettingsRepository
import com.woojik.aircallai.settings.SharedPrefsStore

/**
 * PRD-04: 의존성 구성. 전역 Singleton 남용을 피하기 위해 (PRD-01 req.4)
 * Activity 생성 시점에만 구성하고 ViewModel이 소유한다.
 * Cloud endpoint(baseUrl/model)는 설정에 저장된 값을 턴마다 읽어 사용하며
 * PRD-08에서 endpoint 설정이 세분화된다.
 */
class AppGraph(context: Context) {
    val settings = SettingsRepository(SharedPrefsStore(context))
    val credentials = FileCredentialManager(AppStorage.credentialsDir(context), AndroidKeystoreCrypto())

    val localProvider: AIProvider = LocalAIProvider(NoopLocalModelAdapter())
    val cloudProvider: AIProvider = CloudAIProvider(
        credentials = credentials,
        apiAdapter = HttpCloudApiAdapter(),
        endpointProvider = {
            CloudAIProvider.Endpoint(
                baseUrl = settings.cloudBaseUrl(),
                model = settings.cloudModel(),
            )
        },
    )
    val providerRouter = ProviderRouter(settings, localProvider, cloudProvider)
}
