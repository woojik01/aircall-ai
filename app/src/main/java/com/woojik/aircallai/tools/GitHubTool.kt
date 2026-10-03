package com.woojik.aircallai.tools

class GitHubTool(
    private val api: GitHubApiClient,
) : Tool {
    override val name = "github"
    override val description = "GitHub 저장소와 파일을 읽거나 Issue/PR을 만드는 도구"

    override fun riskFor(action: String) = when (action) {
        "read_repository", "read_file" -> ToolRisk.READ
        // PRD-06: 생성 작업은 WRITE — ToolExecutor의 승인 계층이 허용 전까지 차단한다.
        "create_issue", "create_pull_request" -> ToolRisk.WRITE
        else -> ToolRisk.WRITE
    }

    override suspend fun execute(request: ToolRequest): ToolResult {
        return when (request.action) {
            "read_repository" -> {
                val owner = request.arguments["owner"] ?: return ToolResult(false, "owner가 필요합니다")
                val repo = request.arguments["repo"] ?: return ToolResult(false, "repo가 필요합니다")
                api.readRepository(owner, repo)
            }
            "read_file" -> {
                val owner = request.arguments["owner"] ?: return ToolResult(false, "owner가 필요합니다")
                val repo = request.arguments["repo"] ?: return ToolResult(false, "repo가 필요합니다")
                val path = request.arguments["path"] ?: return ToolResult(false, "path가 필요합니다")
                api.readFile(owner, repo, path)
            }
            "create_issue" -> {
                val owner = request.arguments["owner"] ?: return ToolResult(false, "owner가 필요합니다")
                val repo = request.arguments["repo"] ?: return ToolResult(false, "repo가 필요합니다")
                val title = request.arguments["title"] ?: return ToolResult(false, "title이 필요합니다")
                api.createIssue(owner, repo, title, request.arguments["body"])
            }
            "create_pull_request" -> {
                val owner = request.arguments["owner"] ?: return ToolResult(false, "owner가 필요합니다")
                val repo = request.arguments["repo"] ?: return ToolResult(false, "repo가 필요합니다")
                val title = request.arguments["title"] ?: return ToolResult(false, "title이 필요합니다")
                val head = request.arguments["head"] ?: return ToolResult(false, "head 브랜치가 필요합니다")
                val base = request.arguments["base"] ?: return ToolResult(false, "base 브랜치가 필요합니다")
                api.createPullRequest(owner, repo, title, head, base, request.arguments["body"])
            }
            else -> ToolResult(false, "지원하지 않는 GitHub 작업입니다")
        }
    }
}
