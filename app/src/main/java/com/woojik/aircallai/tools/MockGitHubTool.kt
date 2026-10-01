package com.woojik.aircallai.tools

class MockGitHubTool : Tool {
    override val name = "github"
    override val description = "GitHub 저장소를 조회하거나 변경하는 도구"
    override val risk = ToolRisk.READ
    override suspend fun execute(request: ToolRequest) = when (request.action) {
        "read_repository" -> ToolResult(true, "mock repository read success")
        "create_issue" -> ToolResult(true, "mock issue created")
        "create_pull_request" -> ToolResult(true, "mock pull request created")
        else -> ToolResult(false, "지원하지 않는 GitHub 작업입니다")
    }
}
