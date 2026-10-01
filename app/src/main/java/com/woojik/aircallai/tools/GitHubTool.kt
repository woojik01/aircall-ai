package com.woojik.aircallai.tools

class GitHubTool(
    private val api: GitHubApiClient,
) : Tool {
    override val name = "github"
    override val description = "GitHub 저장소를 조회하는 도구"

    override fun riskFor(action: String) = when (action) {
        "read_repository" -> ToolRisk.READ
        else -> ToolRisk.WRITE
    }

    override suspend fun execute(request: ToolRequest): ToolResult {
        return when (request.action) {
            "read_repository" -> {
                val owner = request.arguments["owner"] ?: return ToolResult(false, "owner가 필요합니다")
                val repo = request.arguments["repo"] ?: return ToolResult(false, "repo가 필요합니다")
                api.readRepository(owner, repo)
            }
            else -> ToolResult(false, "지원하지 않는 GitHub 작업입니다")
        }
    }
}
