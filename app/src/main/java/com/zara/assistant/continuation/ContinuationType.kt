package com.zara.assistant.continuation

/**
 * Layer 6.5C — All continuation signal types.
 * CONFIRM / REJECT: interact with ConfirmationManager.
 * RETRY / RESUME:   interact with RecoveryManager.
 * CONTINUE / NEXT:  interact with WorkflowEngine / ExecutionQueue.
 * CANCEL:           global cancellation.
 * PAUSE / PREVIOUS: architecture-reserved for future media/task controls.
 */
enum class ContinuationType {
    CONFIRM,
    REJECT,
    RETRY,
    RESUME,
    CONTINUE,
    NEXT,
    PREVIOUS,
    PAUSE,
    CANCEL
}
