package dev.happs.aigitassistant.service

import dev.happs.aigitassistant.prompt.AssistantRequestKind
import kotlin.test.Test
import kotlin.test.assertEquals

class GitAssistantToolWindowServiceTest {
    @Test
    fun `applies pending selection when panel registers`() {
        val service = GitAssistantToolWindowService()
        val selectedKinds = mutableListOf<AssistantRequestKind>()

        service.requestKindSelection(AssistantRequestKind.BRANCH_NAME)
        service.registerPanel { requestKind -> selectedKinds += requestKind }

        assertEquals(listOf(AssistantRequestKind.BRANCH_NAME), selectedKinds)
    }

    @Test
    fun `delivers selections to active panel and stops after unregister`() {
        val service = GitAssistantToolWindowService()
        val selectedKinds = mutableListOf<AssistantRequestKind>()
        val panel = GitAssistantToolWindowService.RequestKindSelectionTarget { kind -> selectedKinds += kind }

        service.registerPanel(panel)
        service.requestKindSelection(AssistantRequestKind.COMMIT_MESSAGE)
        service.unregisterPanel(panel)
        service.requestKindSelection(AssistantRequestKind.CHANGE_SUMMARY)

        assertEquals(listOf(AssistantRequestKind.COMMIT_MESSAGE), selectedKinds)
    }
}
