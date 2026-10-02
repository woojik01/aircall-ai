package com.woojik.aircallai

import android.content.Context
import com.woojik.aircallai.ai.cloud.CloudAIProvider
import com.woojik.aircallai.ai.cloud.HttpCloudApiAdapter
import com.woojik.aircallai.ai.local.LocalAIProvider
import com.woojik.aircallai.ai.local.LiteRtModelAdapter
import com.woojik.aircallai.ai.local.ModelDownloadManager
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
import com.woojik.aircallai.tools.GitHubApiClient
import com.woojik.aircallai.tools.GitHubTool
import com.woojik.aircallai.tools.PersistedToolPermissionStore
import com.woojik.aircallai.tools.ToolApprovalCoordinator
import com.woojik.aircallai.tools.ToolExecutor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * 의존성 구성 (PRD-04).
 * PRD-05: 대화 엔진과 세션 상태 통로가 여기서 만들어지며
 * UI(Activity)와 Foreground Service가 동일한 인스턴스를 공유한다.
 * 로컬 기능 증분: 로컬 모델은 LiteRT-LM 어댑터 + 다운로드 갤러리로 구성한다.
 * PRD-06: GitHub Tool(READ/WRITE)과 승인 계층을 그래프에 연결한다.
 * WRITE 승인 상태는 일반 설정에 영속화되어 앱 재시작 후에도 유지된다.
 */
class AppGraph(context: Context) {
    private val settingsStore = SharedPrefsStore(context)
    val settings = SettingsRepository(settingsStore)
    val credentials = FileCredentialManager(AppStorage.credentialsDir(context), AndroidKeystoreCrypto())

    /** 로컬 모델: LiteRT-LM (갤러리에서 선택/다운로드한 .litertlm 모델). */
    val localModelAdapter = LiteRtModelAdapter(context, settings)
    val modelDownloadManager = ModelDownloadManager(context)

    val localProvider: AIProvider = LocalAIProvider(localModelAdapter)
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

    val engine = ConversationEngine(providerRouter.current())

    /** PRD-06: GitHub Tool 실행 계층. 토큰은 CredentialManager(github)에서만 읽는다. */
    val githubApi = GitHubApiClient(credentials)
    val toolPermissions = PersistedToolPermissionStore(settingsStore)
    val toolExecutor = ToolExecutor(listOf(GitHubTool(githubApi)), toolPermissions)

    /** PRD-06: WRITE 작업 승인 흐름. 승인 상태는 toolPermissions에 영속 저장된다. */
    val toolApproval = ToolApprovalCoordinator(
        permissions = toolPermissions,
        executor = toolExecutor,
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
    )

    /**
     * Android 음성 API(SpeechRecognizer/TTS)는 메인 스레드에서 호출한다.
     * 네트워크 요청은 HttpCloudApiAdapter가 Dispatchers.IO로 이동시킨다.
     * 따라서 세션 루프 자체는 Main에서 실행해 Android 오디오 API의 스레드 제약을 지킨다.
     */
    val sessionController = SessionController(
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
    )
    val sessionRepository = SessionRepository(engine, sessionController)
}
