package com.highsockscapital.sunshine.ui

import androidx.compose.runtime.Composable
import com.highsockscapital.sunshine.data.SessionExecutionState

@Composable
fun ConversationDrawer(
    sessions: List<ChatSession>,
    selectedSessionId: String,
    sessionExecutionStates: Map<String, SessionExecutionState>,
    unviewedCompletedSessionIds: Set<String>,
    onNewChat: () -> Unit,
    onSessionSelected: (String) -> Unit,
    onRenameSession: (String, String) -> Unit,
    onExportSession: (ChatSession) -> Unit,
    onDeleteSession: (String) -> Unit,
    onSettingsSelected: () -> Unit,
) {
    SunshineConversationDrawer(
        sessions = sessions.map { session ->
            SharedConversationSummary(
                id = session.id,
                title = session.title,
                indicator = when {
                    sessionExecutionStates[session.id]?.isRunning == true ->
                        SharedConversationIndicator.Working
                    unviewedCompletedSessionIds.contains(session.id) ->
                        SharedConversationIndicator.UnviewedComplete
                    else -> SharedConversationIndicator.None
                },
            )
        },
        selectedSessionId = selectedSessionId,
        onNewChat = onNewChat,
        onSessionSelected = onSessionSelected,
        onRenameSession = onRenameSession,
        onExportSession = { sessionId ->
            sessions.firstOrNull { it.id == sessionId }?.let(onExportSession)
        },
        onDeleteSession = onDeleteSession,
        onSettingsSelected = onSettingsSelected,
        headerContent = {
            SunshineExtensionSlot(SunshineExtensionSlotDrawerHeader)
        },
        footerContent = {
            SunshineExtensionSlot(SunshineExtensionSlotDrawerFooter)
        },
        extraContent = { dismissSearch ->
            SunshineExtensionSlot(SunshineExtensionSlotDrawer)
            SunshineExtensionSlot(SunshineExtensionSlotDrawerListEnd)
        },
    )
}
