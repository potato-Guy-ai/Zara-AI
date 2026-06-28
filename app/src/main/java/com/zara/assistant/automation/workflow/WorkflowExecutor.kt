package com.zara.assistant.automation.workflow

import android.view.accessibility.AccessibilityNodeInfo
import com.zara.assistant.automation.actions.NodeActionExecutor
import com.zara.assistant.automation.actions.NodeActionStatus
import com.zara.assistant.automation.nodes.NodeMatcher
import com.zara.assistant.automation.nodes.NodeScanner
import com.zara.assistant.automation.nodes.ScannedNode
import com.zara.assistant.services.AccessibilityAutomationService
import com.zara.assistant.services.AutomationEvent
import com.zara.assistant.utils.ZaraLogger
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Layer 6.6D Batch 0.5 — WorkflowExecutor.
 *
 * Generic, app-agnostic sequential workflow runner built on top of
 * NodeScanner / NodeMatcher / NodeActionExecutor. No Spotify-specific
 * logic, no retries, no branching, no loops, no recovery — a single
 * deterministic pass through each step, failing immediately on the
 * first failure.
 */
object WorkflowExecutor {

    private const val WAIT_TIMEOUT_MS = 5000L

    private var currentWorkflow: AutomationWorkflow? = null
    private var currentStepIndex: Int = 0
    private var scannedNodes: List<ScannedNode> = emptyList()
    private var matchedNode: AccessibilityNodeInfo? = null

    // Used only by the WAIT_FOR_PACKAGE step to block until onEvent() signals a match.
    private var waitLatch: CountDownLatch? = null
    private var waitTargetPackage: String? = null

    fun start(workflow: AutomationWorkflow): WorkflowResult {
        currentWorkflow = workflow
        currentStepIndex = 0
        scannedNodes = emptyList()
        matchedNode = null
        ZaraLogger.d("[Workflow] started")

        for (index in workflow.steps.indices) {
            currentStepIndex = index
            val failure = executeStep(workflow.steps[index])
            if (failure != null) {
                ZaraLogger.d("[Workflow] failed ${failure.status}")
                return failure
            }
            ZaraLogger.d("[Workflow] step success")
        }

        return WorkflowResult(WorkflowStatus.SUCCESS)
    }

    fun onEvent(event: AutomationEvent) {
        val target = waitTargetPackage ?: return
        if (event.packageName == target) {
            waitLatch?.countDown()
        }
    }

    /** Returns null on success, or a failure WorkflowResult to abort the workflow. */
    private fun executeStep(step: AutomationStep): WorkflowResult? {
        return when (step.type) {
            AutomationStepType.WAIT_FOR_PACKAGE -> waitForPackage(step.value)
            AutomationStepType.SCAN_NODES -> scanNodes()
            AutomationStepType.FIND_BY_TEXT -> findByText(step.value)
            AutomationStepType.CLICK_MATCH -> clickMatch()
        }
    }

    private fun waitForPackage(targetPackage: String?): WorkflowResult? {
        if (targetPackage.isNullOrBlank()) {
            return WorkflowResult(WorkflowStatus.FAILED_STEP, "WAIT_FOR_PACKAGE missing target package")
        }

        val latch = CountDownLatch(1)
        waitLatch = latch
        waitTargetPackage = targetPackage

        val reachedInTime = latch.await(WAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS)

        waitLatch = null
        waitTargetPackage = null

        return if (reachedInTime) null
        else WorkflowResult(WorkflowStatus.FAILED_TIMEOUT, "timed out waiting for $targetPackage")
    }

    private fun scanNodes(): WorkflowResult? {
        val root = AccessibilityAutomationService.instance?.getRootNode()
        val nodes = NodeScanner.scan(root)
        scannedNodes = nodes
        return if (nodes.isEmpty()) {
            WorkflowResult(WorkflowStatus.FAILED_STEP, "SCAN_NODES found no nodes")
        } else null
    }

    private fun findByText(query: String?): WorkflowResult? {
        if (query.isNullOrBlank()) {
            return WorkflowResult(WorkflowStatus.FAILED_STEP, "FIND_BY_TEXT missing search text")
        }
        val match = NodeMatcher.findByText(scannedNodes, query)
        matchedNode = match?.node
        return if (match == null) {
            WorkflowResult(WorkflowStatus.FAILED_STEP, "FIND_BY_TEXT no match for \"$query\"")
        } else null
    }

    private fun clickMatch(): WorkflowResult? {
        val result = NodeActionExecutor.click(matchedNode)
        return if (result.status != NodeActionStatus.SUCCESS) {
            WorkflowResult(WorkflowStatus.FAILED_STEP, result.message ?: "CLICK_MATCH failed: ${result.status}")
        } else null
    }
}
