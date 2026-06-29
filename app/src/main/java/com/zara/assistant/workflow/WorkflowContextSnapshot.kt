package com.zara.assistant.workflow

import com.zara.assistant.context.AppContext
import com.zara.assistant.context.ConversationContextManager
import com.zara.assistant.context.MediaContext
import com.zara.assistant.context.PersonContext
import com.zara.assistant.context.QueryContext

/**
 * Layer 6.5B — Context Snapshot.
 *
 * Captures the live context state at the moment a workflow starts.
 * All fields are read-only copies. Mutations to ConversationContextManager
 * after workflow start do NOT affect this snapshot.
 *
 * Use case: "call boss then message him"
 *   Step 1 executes CALL(boss) — ContextManager updates lastPerson to boss.
 *   Step 2 pronouns resolve from the snapshot, not the post-step-1 state.
 *   This guarantees consistency across steps.
 */
data class WorkflowContextSnapshot(
    val person: PersonContext?,
    val app: AppContext?,
    val media: MediaContext?,
    val query: QueryContext?
) {
    companion object {
        /** Capture current live context. Call once when workflow is created. */
        fun capture(): WorkflowContextSnapshot = WorkflowContextSnapshot(
            person = ConversationContextManager.lastPerson(),
            app    = ConversationContextManager.lastApp(),
            media  = ConversationContextManager.lastMedia(),
            query  = ConversationContextManager.lastQuery()
        )
    }
}
