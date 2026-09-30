package com.woojik.aircallai

import android.content.Context
import com.woojik.aircallai.ai.cloud.CloudAIProvider
import com.woojik.aircallai.ai.cloud.HttpCloudApiAdapter
import com.woojik.aircallai.ai.local.LocalAIProvider
import com.woojik.aircallai.ai.local.NoopLocalModelAdapter
import com.woojik.aircallai.ai.provider.AIProvider
import com.woojik.aircallai.ai.provider.ProviderRouter
import com.woojik.aircallai.conversation.ConversationEngine
import com.woojik.aircallai.core.security.AndroidKeystoreCrypto
import com.woojik.aircallai.core.storage.AppStorage
import com.woojik.aircallai.core.storage.FileCredentialManager
import com.woojik.aircallai.session.SessionController
import com.woojik.aircallai.session.SessionRepository
import com.woojik.aircallai.settings.SettingsRepository
import com.woojik.aircallai.settings.SharedPrefsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * 의존성 구성 (PRD-04).
 * PRD-05: 대화 엔진과 세션 상태 통로가 여기서 만들어지며
 * UI(Activity)와 Foreground Service가 동일한 인스턴스를 공유한다.
 * 전역 Singleton 남용이 아니라 App 범위의 단일 구성 루트다 (PRD-01 req.4).
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

    /** PRD-05: 세션 상태는 Activity 밖(백그라운드 포함)에서 유지된다. */
    val engine = ConversationEngine(providerRouter.current())
    val sessionController = SessionController(CoroutineScope(SupervisorJob() + Dispatchers.Default))
    val sessionRepository = SessionRepository(engine, sessionController)
}
