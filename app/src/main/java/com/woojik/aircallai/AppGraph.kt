package com.woojik.aircallai

import android.content.Context
import com.woojik.aircallai.ai.cloud.CloudAIProvider
import com.woojik.aircallai.ai.cloud.HttpCloudApiAdapter
import com.woojik.aircallai.ai.local.LocalAIProvider
import com.woojik.aircallai.ai.local.LiteRtModelAdapter
import com.woojik.aircallai.ai.local.ModelDownloadManager
import com.woojik.aircallai.ai.provider.AIProvider
import com.woojik.aircallai.ai.provider.ProviderRouter
import com.woojik.aircallai.auth.AccountConnectionRepository
import com.woojik.aircallai.auth.GitHubDeviceFlowClient
import com.woojik.aircallai.auth.GoogleOAuthClient
import com.woojik.aircallai.auth.HttpOAuthPost
import com.woojik.aircallai.auth.OAuthCredentialStore
import com.woojik.aircallai.conversation.ConversationEngine
import com.woojik.aircallai.core.security.AndroidKeystoreCrypto
import com.woojik.aircallai.core.storage.AppStorage
import com.woojik.aircallai.core.storage.FileCredentialManager
import com.woojik.aircallai.session.SessionController
import com.woojik.aircallai.session.SessionRepository
import com.woojik.aircallai.settings.SettingsRepository
import com.woojik.aircallai.settings.SharedPrefsStore
import com.woojik.aircallai.tools.FileNotesStore
import com.woojik.aircallai.tools.GitHubApiClient
import com.woojik.aircallai.tools.GitHubTool
import com.woojik.aircallai.tools.GmailApiClient
import com.woojik.aircallai.tools.GmailTool
import com.woojik.aircallai.tools.NotesStore
import com.woojik.aircallai.tools.NotesTool
import com.woojik.aircallai.tools.PersistedToolPermissionStore
import com.woojik.aircallai.tools.ToolApprovalCoordinator
import com.woojik.aircallai.tools.ToolBridgedAIProvider
import com.woojik.aircallai.tools.ToolExecutionLogger
import com.woojik.aircallai.tools.ToolExecutor
import com.woojik.aircallai.tools.AndroidCalendarAdapter
import com.woojik.aircallai.tools.CalendarAdapter
import com.woojik.aircallai.tools.CalendarTool
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * 의존성 구성 (PRD-04).
 * PRD-05: 대화 엔진과 세션 상태 통로가 여기서 만들어지며
 * UI(Activity)와 Foreground Service가 동일한 인스턴스를 공유한다.
 * 로컬 기능 증분: 로컬 모델은 LiteRT-LM 어댑터 + 다운로드 갤러리로 구성한다.
 * PRD-06: GitHub/Notes/Calendar/Gmail Tool과 승인 계층을 그래프에 연결한다.
 * WRITE 승인 상태는 일반 설정에 영속화되어 앱 재시작 후에도 유지된다.
 * Tool-AI 연결: 모든 provider를 ToolBridgedAIProvider로 감싸 Tool 지시어를 처리한다.
 * PRD-09: GitHub(기기 인증)/Google(PKCE) OAuth 계층과 연결 저장소를 구성한다.
 * 소셜 로그인 UX: OAuth Client ID 기본값은 빌드 시점(BuildConfig)에서 제공한다.
 * 연결된 토큰은 CredentialManager에 암호화 저장되고 기존 Tool 계층과 동기화된다.
 */
class AppGraph(context: Context) {
    private val settingsStore = SharedPrefsStore(context)
    val settings = SettingsRepository(
        settingsStore,
        defaultGithubOAuthClientId = BuildConfig.GITHUB_OAUTH_CLIENT_ID,
        defaultGoogleOAuthClientId = BuildConfig.GOOGLE_OAUTH_CLIENT_ID,
    )
    val credentials = FileCredentialManager(AppStorage.credentialsDir(context), AndroidKeystoreCrypto())

    /** 로컬 모델: LiteRT-LM (갤러리에서 선택/다운로드한 .litertlm 모델). */
    val localModelAdapter = LiteRtModelAdapter(context, settings)
    val modelDownloadManager = ModelDownloadManager(context)

    /** PRD-06: Tool 실행 계층. 각 도구의 인증 정보는 CredentialManager에서만 읽는다. */
    val githubApi = GitHubApiClient(credentials)
    val gmailApi = GmailApiClient(credentials)
    val notesStore: NotesStore = FileNotesStore(File(context.filesDir, "tools/notes.txt"))
    val calendarAdapter: CalendarAdapter = AndroidCalendarAdapter(context)
    val toolPermissions = PersistedToolPermissionStore(settingsStore)
    val toolLogger = ToolExecutionLogger()
    val toolExecutor = ToolExecutor(
        listOf(
            GitHubTool(githubApi),
            NotesTool(notesStore),
            CalendarTool(calendarAdapter),
            GmailTool(gmailApi),
        ),
        toolPermissions,
    )

    /** PRD-06: WRITE 작업 승인 흐름. 승인 상태는 toolPermissions에 영속 저장된다. */
    val toolApproval = ToolApprovalCoordinator(
        permissions = toolPermissions,
        executor = toolExecutor,
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
    )

    /** PRD-09: OAuth 계층. 토큰은 Keystore 암호화 저장되고 로그에 노출되지 않는다. */
    val oauthHttp = HttpOAuthPost()
    val oauthStore = OAuthCredentialStore(credentials)
    val githubAuth = GitHubDeviceFlowClient(
        http = oauthHttp,
        clientIdProvider = { settings.githubOAuthClientId() },
    )
    val googleAuth = GoogleOAuthClient(
        http = oauthHttp,
        clientIdProvider = { settings.googleOAuthClientId() },
    )
    val accountRepository = AccountConnectionRepository(oauthStore, listOf(githubAuth, googleAuth))

    /** Tool-AI 연결: AI 응답의 TOOL 지시어를 실행하고 결과를 반영한다. */
    private val toolCatalog =
        "github.read_repository owner=<소유자> repo=<저장소> — GitHub 저장소를 조회한다\n" +
            "github.create_issue owner=<소유자> repo=<저장소> title=<제목> [body=<내용>] — Issue를 만든다 (승인 필요)\n" +
            "github.create_pull_request owner=<소유자> repo=<저장소> title=<제목> head=<브랜치> base=<브랜치> — PR을 만든다 (승인 필요)\n" +
            "notes.add_note text=<내용> — 메모를 기기에 저장한다 (승인 필요)\n" +
            "notes.search_notes query=<검색어> — 메모를 검색한다\n" +
            "notes.list_notes [limit=<개수>] — 최근 메모를 나열한다\n" +
            "calendar.read_upcoming [limit=<개수>] — 다가오는 일정을 조회한다\n" +
            "calendar.create_event title=<제목> start=\"YYYY-MM-DD HH:MM\" [duration_minutes=<분>] — 일정을 등록한다 (승인 필요)\n" +
            "gmail.send_email to=<주소> subject=<제목> body=<내용> — 이메일을 보낸다 (승인 필요)"

    val localProvider: AIProvider = ToolBridgedAIProvider(
        base = LocalAIProvider(localModelAdapter),
        executor = toolExecutor,
        logger = toolLogger,
        toolsDescription = toolCatalog,
        approvalRequester = { request -> toolApproval.submit(request) },
    )
    val cloudProvider: AIProvider = ToolBridgedAIProvider(
        base = CloudAIProvider(
            credentials = credentials,
            apiAdapter = HttpCloudApiAdapter(),
            endpointProvider = {
                CloudAIProvider.Endpoint(
                    baseUrl = settings.cloudBaseUrl(),
                    model = settings.cloudModel(),
                )
            },
        ),
        executor = toolExecutor,
        logger = toolLogger,
        toolsDescription = toolCatalog,
        approvalRequester = { request -> toolApproval.submit(request) },
    )

    val providerRouter = ProviderRouter(settings, localProvider, cloudProvider)

    val engine = ConversationEngine(providerRouter.current())

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
