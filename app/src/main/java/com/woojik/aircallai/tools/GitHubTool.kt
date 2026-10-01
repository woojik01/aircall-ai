package com.woojik.aircallai.tools

class GitHubTool(
    private val api: GitHubApiClient,
) : Tool {
    override val name = "github"
    override val description = "GitHub 저장소 조회, Issue/PR 생성 도구"

    override val actions = listOf(
        ToolActionSpec(ACTION_GET_USER, "토큰의 GitHub 로그인 사용자 확인"),
        ToolActionSpec(ACTION_READ_REPOSITORY, "저장소 정보 조회", listOf("owner", "repo")),
        ToolActionSpec(ACTION_CREATE_ISSUE, "Issue 생성 (사용자 승인 필요)", listOf("owner", "repo", "title"), listOf("body")),
        ToolActionSpec(
            ACTION_CREATE_PULL_REQUEST,
            "Pull Request 생성 (사용자 승인 필요)",
            listOf("owner", "repo", "title", "head", "base"),
            listOf("body"),
        ),
    )

    override fun riskFor(action: String) = when (action) {
        ACTION_GET_USER, ACTION_READ_REPOSITORY -> ToolRisk.READ
        ACTION_CREATE_ISSUE, ACTION_CREATE_PULL_REQUEST -> ToolRisk.WRITE
        else -> if (action.startsWith("delete")) ToolRisk.DESTRUCTIVE else ToolRisk.WRITE
    }

    override suspend fun execute(request: ToolRequest): ToolResult {
        val args = request.arguments
        if (request.action == ACTION_GET_USER) return api.currentUser()
        val owner = args["owner"] ?: return ToolResult(false, "owner가 필요합니다")
        val repo = args["repo"] ?: return ToolResult(false, "repo가 필요합니다")
        return when (request.action) {
            ACTION_READ_REPOSITORY -> api.readRepository(owner, repo)
            ACTION_CREATE_ISSUE -> {
                val title = args["title"] ?: return ToolResult(false, "title이 필요합니다")
                api.createIssue(owner, repo, title, args["body"])
            }
            ACTION_CREATE_PULL_REQUEST -> {
                val title = args["title"] ?: return ToolResult(false, "title이 필요합니다")
                val head = args["head"] ?: return ToolResult(false, "head가 필요합니다")
                val base = args["base"] ?: return ToolResult(false, "base가 필요합니다")
                api.createPullRequest(owner, repo, title, head, base, args["body"])
            }
            else -> ToolResult(false, "지원하지 않는 GitHub 작업입니다")
        }
    }

    companion object {
        const val ACTION_GET_USER = "get_user"
        const val ACTION_READ_REPOSITORY = "read_repository"
        const val ACTION_CREATE_ISSUE = "create_issue"
        const val ACTION_CREATE_PULL_REQUEST = "create_pull_request"

        /** 설정 화면에서 사용자가 개별 승인할 수 있는 WRITE 작업. */
        val APPROVABLE_ACTIONS = listOf(ACTION_CREATE_ISSUE, ACTION_CREATE_PULL_REQUEST)
    }
}
