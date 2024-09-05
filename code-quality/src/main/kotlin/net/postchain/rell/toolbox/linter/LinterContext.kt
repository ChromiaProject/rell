package net.postchain.rell.toolbox.linter

class LinterContext {
    private val issues = mutableListOf<LinterIssue>()
    fun addIssue(issue: LinterIssue) = issues.add(issue)
    fun getIssues(): List<LinterIssue> = issues
}
