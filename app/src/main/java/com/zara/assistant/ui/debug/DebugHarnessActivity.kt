package com.zara.assistant.ui.debug

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zara.assistant.automation.actions.NodeActionExecutor
import com.zara.assistant.automation.nodes.NodeMatcher
import com.zara.assistant.automation.nodes.NodeScanner
import com.zara.assistant.automation.workflow.AutomationStep
import com.zara.assistant.automation.workflow.AutomationStepType
import com.zara.assistant.automation.workflow.AutomationWorkflow
import com.zara.assistant.automation.workflow.WorkflowExecutor
import com.zara.assistant.services.AccessibilityAutomationService
import com.zara.assistant.ui.theme.ZaraTheme
import com.zara.assistant.utils.ZaraLogger
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

// ============================================================
// TEMP DEBUG HARNESS — REMOVE AFTER SPOTIFY STABILIZATION
// ============================================================
// Manual on-device validation for the Phase 0 UI Automation foundation:
// event propagation, WAIT_FOR_PACKAGE, node scanning, node matching,
// click execution. Entirely isolated in ui/debug/ — deleting this file
// (+ its manifest entry + the one launcher button in MainActivity) fully
// removes this harness. Does NOT touch PlaybackOrchestrator,
// FreePlaybackEngine, PremiumPlaybackEngine, or the voice flow.
class DebugHarnessActivity : ComponentActivity() {

    // TEMP DEBUG HARNESS — REMOVE AFTER SPOTIFY STABILIZATION
    // WorkflowExecutor.start() blocks (up to 5s) and refuses to run on the
    // main thread (Batch 0.5A guard) — every test here runs off a single
    // dedicated background executor.
    private val bg: ExecutorService = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ZaraTheme { DebugHarnessScreen(onRunOnBackground = { bg.execute(it) }) } }
    }

    override fun onDestroy() {
        bg.shutdown()
        super.onDestroy()
    }
}

// TEMP DEBUG HARNESS — REMOVE AFTER SPOTIFY STABILIZATION
private const val DEBUG_SPOTIFY_PACKAGE = "com.spotify.music"
private const val DEBUG_SEARCH_QUERY = "Search"

// TEMP DEBUG HARNESS — REMOVE AFTER SPOTIFY STABILIZATION
@Composable
private fun DebugHarnessScreen(onRunOnBackground: (() -> Unit) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Phase 0 Automation Debug Harness", style = MaterialTheme.typography.titleMedium)

        Button(onClick = { onRunOnBackground { debugTestEventFlow() } }, modifier = Modifier.fillMaxWidth()) {
            Text("Test Event Flow")
        }
        Button(onClick = { onRunOnBackground { debugTestWaitSpotify() } }, modifier = Modifier.fillMaxWidth()) {
            Text("Test Wait Spotify")
        }
        Button(onClick = { onRunOnBackground { debugTestScanNodes() } }, modifier = Modifier.fillMaxWidth()) {
            Text("Test Scan Nodes")
        }
        Button(onClick = { onRunOnBackground { debugTestMatchSearch() } }, modifier = Modifier.fillMaxWidth()) {
            Text("Test Match Search")
        }
        Button(onClick = { onRunOnBackground { debugTestClickSearch() } }, modifier = Modifier.fillMaxWidth()) {
            Text("Test Click Search")
        }
    }
}

// TEMP DEBUG HARNESS — REMOVE AFTER SPOTIFY STABILIZATION
// Button 1: Test Event Flow — runs WAIT_FOR_PACKAGE(com.spotify.music);
// open Spotify manually after tapping to drive the event through
// AccessibilityAutomationService -> UiAutomationEngine -> WorkflowExecutor.
private fun debugTestEventFlow() {
    ZaraLogger.d("[Debug] Event flow test started")
    val workflow = AutomationWorkflow(
        workflowId = "debug-event-flow",
        steps = listOf(AutomationStep(AutomationStepType.WAIT_FOR_PACKAGE, DEBUG_SPOTIFY_PACKAGE))
    )
    val result = WorkflowExecutor.start(workflow)
    ZaraLogger.d("[Debug] Event flow test result=${result.status} message=${result.message}")
}

// TEMP DEBUG HARNESS — REMOVE AFTER SPOTIFY STABILIZATION
// Button 2: Test Wait Spotify — same WAIT_FOR_PACKAGE step in isolation,
// existing 5s timeout from WorkflowExecutor applies unchanged.
private fun debugTestWaitSpotify() {
    ZaraLogger.d("[Debug] Wait Spotify test started")
    val workflow = AutomationWorkflow(
        workflowId = "debug-wait-spotify",
        steps = listOf(AutomationStep(AutomationStepType.WAIT_FOR_PACKAGE, DEBUG_SPOTIFY_PACKAGE))
    )
    val result = WorkflowExecutor.start(workflow)
    ZaraLogger.d("[Debug] Wait Spotify test result=${result.status} message=${result.message}")
}

// TEMP DEBUG HARNESS — REMOVE AFTER SPOTIFY STABILIZATION
// Button 3: Test Scan Nodes — fails gracefully (no crash) if the
// accessibility service isn't connected or there's no root node yet.
private fun debugTestScanNodes() {
    ZaraLogger.d("[Debug] scan started")
    val service = AccessibilityAutomationService.instance
    if (service == null) {
        ZaraLogger.d("[Debug] scan failed: AccessibilityAutomationService unavailable")
        return
    }
    val root = service.getRootNode()
    if (root == null) {
        ZaraLogger.d("[Debug] scan failed: root node is null")
        return
    }
    val nodes = NodeScanner.scan(root)
    ZaraLogger.d("[Debug] scan complete nodeCount=${nodes.size}")
}

// TEMP DEBUG HARNESS — REMOVE AFTER SPOTIFY STABILIZATION
// Button 4: Test Match Search — scan then NodeMatcher.findByText("Search").
private fun debugTestMatchSearch() {
    ZaraLogger.d("[Debug] match started")
    val root = AccessibilityAutomationService.instance?.getRootNode()
    if (root == null) {
        ZaraLogger.d("[Debug] match failed: service unavailable or root null")
        return
    }
    val nodes = NodeScanner.scan(root)
    val match = NodeMatcher.findByText(nodes, DEBUG_SEARCH_QUERY)
    if (match == null) {
        ZaraLogger.d("[Debug] match failed: no match for \"$DEBUG_SEARCH_QUERY\"")
    } else {
        ZaraLogger.d("[Debug] match found score=${match.score}")
    }
}

// TEMP DEBUG HARNESS — REMOVE AFTER SPOTIFY STABILIZATION
// Button 5: Test Click Search — scan -> match -> click pipeline.
private fun debugTestClickSearch() {
    ZaraLogger.d("[Debug] click test started")
    val root = AccessibilityAutomationService.instance?.getRootNode()
    if (root == null) {
        ZaraLogger.d("[Debug] click failed: service unavailable or root null")
        return
    }
    val nodes = NodeScanner.scan(root)
    val match = NodeMatcher.findByText(nodes, DEBUG_SEARCH_QUERY)
    if (match == null) {
        ZaraLogger.d("[Debug] click failed: no match for \"$DEBUG_SEARCH_QUERY\"")
        return
    }
    val result = NodeActionExecutor.click(match.node)
    ZaraLogger.d("[Debug] click test result=${result.status} message=${result.message}")
}
