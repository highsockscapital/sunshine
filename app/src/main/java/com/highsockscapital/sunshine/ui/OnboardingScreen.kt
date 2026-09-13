package com.highsockscapital.sunshine.ui

import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.highsockscapital.sunshine.data.AgentModeAuthorizationMethod
import com.highsockscapital.sunshine.data.LlmProviderConfig
import com.highsockscapital.sunshine.data.ProviderAuthMethod
import com.highsockscapital.sunshine.data.sortedByPreferredModelName
import com.highsockscapital.sunshine.data.RootSetupIssue
import com.highsockscapital.sunshine.data.RootSetupState
import com.highsockscapital.sunshine.data.pi.PiCoreSetupState
import com.highsockscapital.sunshine.data.pi.PiProviderAuthState
import com.highsockscapital.sunshine.termux.TermuxSetupIssue
import com.highsockscapital.sunshine.termux.TermuxSetupState
import com.highsockscapital.sunshine.termux.TermuxContract
import com.highsockscapital.sunshine.R
import com.highsockscapital.sunshine.ui.theme.SunshineOnSurface
import com.highsockscapital.sunshine.ui.theme.SunshineOnSurfaceVariant
import com.highsockscapital.sunshine.ui.theme.SunshinePrimary
import com.highsockscapital.sunshine.ui.theme.SunshineSecondary
import com.highsockscapital.sunshine.ui.theme.SunshineSurfaceHigh
import com.highsockscapital.sunshine.ui.theme.SunshineTertiary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val StepFadeDuration = 560

private val TourEasing = CubicBezierEasing(0.22f, 0.84f, 0.18f, 1f)
private val InitialOnboardingSteps = listOf(
    OnboardingStep.Landing,
    OnboardingStep.ProviderSetup,
)
private val FollowUpOnboardingSteps = listOf(
    OnboardingStep.LocalRuntimeChoice,
    OnboardingStep.TermuxSetup,
    OnboardingStep.AgentModeAuthorization,
)

private val TourTextPrimary: Color
    get() = SunshineOnSurface
private val TourTextSecondary: Color
    get() = SunshineOnSurfaceVariant
private val TourSurface: Color
    get() = SunshineSurfaceHigh
private val TourBlue: Color
    get() = SunshinePrimary
private val TourGreen: Color
    get() = SunshineSecondary
private val TourGold: Color
    get() = SunshineTertiary
private val TourPurple: Color
    get() = SunshinePrimary


private enum class ProviderTourStage {
    PickAuthentication,
    PickProvider,
    Credentials,
    Model,
}

@Composable
fun OnboardingScreen(
    initialStep: OnboardingStep,
    replayMode: Boolean,
    existingProviderConfig: LlmProviderConfig?,
    isFetchingModels: Boolean,
    providerAuthState: PiProviderAuthState,
    piCoreSetupState: PiCoreSetupState,
    termuxSetupState: TermuxSetupState,
    rootSetupState: RootSetupState,
    agentModeAuthorizationMethod: AgentModeAuthorizationMethod,
    tavilyApiKey: String,
    setupPreviewMode: Boolean = false,
    onFetchModels: (LlmProviderConfig, (List<String>) -> Unit) -> Unit,
    onStartProviderLogin: (String, String, ProviderAuthMethod, String) -> Unit,
    onSubmitProviderAuthPrompt: (String, String, Boolean) -> Unit,
    onClearProviderAuthState: () -> Unit,
    onSkip: () -> Unit,
    onClose: () -> Unit,
    onCompleteProviderSetup: (LlmProviderConfig) -> Unit,
    onSaveTavilyApiKey: (String) -> Unit,
    onRequestTermuxPermission: () -> Unit,
    onOpenAppPermissions: () -> Unit,
    onOpenTermuxSettings: () -> Unit,
    onOpenTermux: () -> Unit,
    onInstallTermux: () -> Unit,
    onRefreshTermuxSetup: () -> Unit,
    onRefreshRootSetup: () -> Unit,
    onConfigureWithRoot: () -> Unit,
    onSaveAgentModeAuthorization: (Boolean, AgentModeAuthorizationMethod) -> Unit,
    onCompleteFollowUp: () -> Unit,
) {

    var currentStep by rememberSaveable(initialStep, replayMode) {
        mutableStateOf(initialStep)
    }
    var tavilyApiKeyValue by rememberSaveable(initialStep, replayMode, tavilyApiKey) {
        mutableStateOf(tavilyApiKey)
    }
    val formState = rememberProviderFormState(existingProviderConfig)
    var selectedRuntimePath by rememberSaveable(initialStep, replayMode) {
        mutableStateOf<OnboardingStep?>(null)
    }
    val isInitialFlow = initialStep == OnboardingStep.Landing ||
        initialStep == OnboardingStep.ProviderSetup ||
        false
    val steps = remember(initialStep) {
        if (isInitialFlow) InitialOnboardingSteps else FollowUpOnboardingSteps
    }

    fun indexOf(step: OnboardingStep): Int = steps.indexOf(step).coerceAtLeast(0)
    fun continueAfterLocalAccessSetup() {
        onCompleteFollowUp()
    }
    fun continueAfterTermuxStep() {
        if (termuxSetupState.isReady) currentStep = OnboardingStep.AgentModeAuthorization
        else onCompleteFollowUp()
    }

    AnimatedContent(
        targetState = currentStep,
        transitionSpec = {
            fadeIn(
                animationSpec = tween(
                    durationMillis = StepFadeDuration,
                    delayMillis = 180,
                    easing = TourEasing,
                )
            ) togetherWith fadeOut(
                animationSpec = tween(
                    durationMillis = 180,
                    easing = TourEasing,
                )
            )
        },
        label = "onboarding_step_transition",
    ) { step ->
        val stepIndex = indexOf(step) + 1
        when (step) {
            OnboardingStep.Landing -> LandingStep(
                stepIndex = stepIndex,
                stepCount = steps.size,
                replayMode = replayMode,
                onPrimary = { currentStep = OnboardingStep.ProviderSetup },
                onSecondary = if (replayMode) onClose else onSkip,
            )

            OnboardingStep.ProviderSetup -> ProviderSetupStep(
                stepIndex = stepIndex,
                stepCount = steps.size,
                replayMode = replayMode,
                formState = formState,
                isFetchingModels = isFetchingModels,
                onFetchModels = onFetchModels,
                authState = providerAuthState,
                onStartProviderLogin = onStartProviderLogin,
                onSubmitAuthPrompt = onSubmitProviderAuthPrompt,
                onClearAuthState = onClearProviderAuthState,
                onExit = if (replayMode) onClose else onSkip,
                onClose = if (replayMode) onClose else onSkip,
                onReturnToLanding = { currentStep = OnboardingStep.Landing },
                onComplete = { onCompleteProviderSetup(formState.buildConfig()) },
            )

            OnboardingStep.LocalRuntimeChoice -> LocalRuntimeChoiceStep(
                stepIndex = stepIndex,
                stepCount = steps.size,
                onClose = onClose,
                onConfigure = {
                    selectedRuntimePath = OnboardingStep.TermuxSetup
                    currentStep = OnboardingStep.TermuxSetup
                },
                onSkip = {
                    selectedRuntimePath = null
                    onCompleteFollowUp()
                },
            )

            OnboardingStep.TermuxSetup -> TermuxStep(
                stepIndex = stepIndex,
                stepCount = steps.size,
                setupState = termuxSetupState,
                rootSetupState = rootSetupState,
                onClose = onClose,
                onBack = { currentStep = OnboardingStep.LocalRuntimeChoice },
                onContinue = ::continueAfterTermuxStep,
                onRootConfigured = ::continueAfterLocalAccessSetup,
                onRequestPermission = onRequestTermuxPermission,
                onOpenAppPermissions = onOpenAppPermissions,
                onOpenTermuxSettings = onOpenTermuxSettings,
                onOpenTermux = onOpenTermux,
                onInstallTermux = onInstallTermux,
                onRefresh = onRefreshTermuxSetup,
                onRefreshRootSetup = onRefreshRootSetup,
                onConfigureWithRoot = onConfigureWithRoot,
            )

            OnboardingStep.AgentModeAuthorization -> AgentModeAuthorizationStep(
                stepIndex = stepIndex,
                stepCount = steps.size,
                initialMethod = agentModeAuthorizationMethod,
                onBack = { currentStep = OnboardingStep.TermuxSetup },
                onClose = onClose,
                onContinue = { enabled, method ->
                    onSaveAgentModeAuthorization(enabled, method)
                    onCompleteFollowUp()
                },
            )

            OnboardingStep.TavilySetup -> LaunchedEffect(Unit) { onCompleteFollowUp() }
        }
    }
}

@Composable
private fun LandingStep(
    stepIndex: Int,
    stepCount: Int,
    replayMode: Boolean,
    onPrimary: () -> Unit,
    onSecondary: () -> Unit,
) {
    OnboardingLandingStep(
        stepIndex = stepIndex,
        stepCount = stepCount,
        replayMode = replayMode,
        onPrimary = onPrimary,
        onSecondary = onSecondary,
    )
}


@Composable
private fun ConversationStepPage(
    stepIndex: Int,
    stepCount: Int,
    message: String,
    onBack: (() -> Unit)?,
    topRightLabel: String,
    onTopRight: () -> Unit,
    isExiting: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    OnboardingConversationStepPage(
        stepIndex = stepIndex,
        stepCount = stepCount,
        message = message,
        onBack = onBack,
        topRightLabel = topRightLabel,
        onTopRight = onTopRight,
        isExiting = isExiting,
        content = content,
    )
}

@Composable
private fun ProviderSetupStep(
    stepIndex: Int,
    stepCount: Int,
    replayMode: Boolean,
    formState: ProviderFormState,
    isFetchingModels: Boolean,
    onFetchModels: (LlmProviderConfig, (List<String>) -> Unit) -> Unit,
    authState: PiProviderAuthState,
    onStartProviderLogin: (String, String, ProviderAuthMethod, String) -> Unit,
    onSubmitAuthPrompt: (String, String, Boolean) -> Unit,
    onClearAuthState: () -> Unit,
    onExit: () -> Unit,
    onClose: () -> Unit,
    onReturnToLanding: () -> Unit,
    onComplete: () -> Unit,
) {
    var stageIndex by rememberSaveable(stepIndex, replayMode) { mutableStateOf(0) }
    var isFinishing by rememberSaveable(stepIndex, replayMode) { mutableStateOf(false) }
    val message = when (stageIndex) {
        1 -> stringResource(R.string.onboarding_provider_pick_message)
        2 -> stringResource(R.string.onboarding_provider_credentials_message)
        3 -> stringResource(R.string.onboarding_provider_model_message)
        else -> stringResource(R.string.onboarding_provider_auth_message)
    }

    LaunchedEffect(isFinishing) {
        if (isFinishing) {
            delay(320)
            onComplete()
        }
    }

    ConversationStepPage(
        stepIndex = stepIndex,
        stepCount = stepCount,
        message = message,
        onBack = onReturnToLanding,
        topRightLabel = if (replayMode) {
            stringResource(R.string.common_close)
        } else {
            stringResource(R.string.common_skip)
        },
        onTopRight = if (replayMode) onClose else onExit,
        isExiting = isFinishing,
    ) {
        AddProviderWizard(
            state = formState,
            existingProviderIds = emptySet(),
            isFetchingModels = isFetchingModels,
            onFetchModels = onFetchModels,
            authState = authState,
            onStartProviderLogin = onStartProviderLogin,
            onSubmitAuthPrompt = onSubmitAuthPrompt,
            onClearAuthState = onClearAuthState,
            onSave = { isFinishing = true },
            saveLabel = stringResource(R.string.common_start_chat),
            onStageChanged = { stageIndex = it },
        )
    }
}


@Composable
private fun LocalRuntimeChoiceStep(
    stepIndex: Int,
    stepCount: Int,
    onClose: () -> Unit,
    onConfigure: () -> Unit,
    onSkip: () -> Unit,
) {
    ConversationStepPage(
        stepIndex = stepIndex,
        stepCount = stepCount,
        message = stringResource(R.string.onboarding_local_runtime_choice_message),
        onBack = null,
        topRightLabel = stringResource(R.string.common_close),
        onTopRight = onClose,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            StepLead(
                icon = Icons.Rounded.Terminal,
                accent = TourGreen,
                title = stringResource(R.string.onboarding_termux_agent_mode_title),
                body = stringResource(R.string.onboarding_termux_agent_mode_subtitle),
            )
            TourActionRow(
                primaryLabel = stringResource(R.string.onboarding_configure_termux_agent_mode),
                onPrimary = onConfigure,
                secondaryLabel = stringResource(R.string.onboarding_not_now),
                onSecondary = onSkip,
            )
        }
    }
}


@Composable
private fun TermuxStep(
    stepIndex: Int,
    stepCount: Int,
    setupState: TermuxSetupState,
    rootSetupState: RootSetupState,
    onClose: () -> Unit,
    onBack: () -> Unit,
    onContinue: () -> Unit,
    onRootConfigured: () -> Unit,
    onRequestPermission: () -> Unit,
    onOpenAppPermissions: () -> Unit,
    onOpenTermuxSettings: () -> Unit,
    onOpenTermux: () -> Unit,
    onInstallTermux: () -> Unit,
    onRefresh: () -> Unit,
    onRefreshRootSetup: () -> Unit,
    onConfigureWithRoot: () -> Unit,
) {

    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val setupCommandCopiedLabel = stringResource(R.string.onboarding_termux_setup_command_copied)
    var shouldAutoContinue by rememberSaveable(stepIndex) { mutableStateOf(!setupState.isReady) }
    var showRootSetupPrompt by rememberSaveable(stepIndex) { mutableStateOf(true) }
    fun copyTermuxSetupCommand() {
        clipboardManager.setText(AnnotatedString(TermuxContract.ExternalAppsSetupCommand))
        Toast.makeText(
            context,
            setupCommandCopiedLabel,
            Toast.LENGTH_SHORT,
        ).show()
    }
    fun copyTermuxSetupCommandAndOpenTermux() {
        copyTermuxSetupCommand()
        onOpenTermux()
    }

    LaunchedEffect(stepIndex) {
        onRefreshRootSetup()
    }
    LaunchedEffect(showRootSetupPrompt, rootSetupState.isReady) {
        if (showRootSetupPrompt && rootSetupState.isReady) {
            delay(820)
            onRootConfigured()
        }
    }
    LaunchedEffect(showRootSetupPrompt, setupState.isReady) {
        if (!showRootSetupPrompt && shouldAutoContinue && setupState.isReady) {
            shouldAutoContinue = false
            delay(820)
            onContinue()
        }
    }

    ConversationStepPage(
        stepIndex = stepIndex,
        stepCount = stepCount,
message = stringResource(R.string.onboarding_termux_message),
        onBack = onBack,
        topRightLabel = stringResource(R.string.common_close),
        onTopRight = onClose,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            if (showRootSetupPrompt) {
                RootSetupPrompt(
                    rootSetupState = rootSetupState,
                    onUseRoot = onConfigureWithRoot,
                    onContinueManual = { showRootSetupPrompt = false },
                    onRootConfigured = onRootConfigured,
                    onInstallTermux = onInstallTermux,
                )
            } else {
                StepLead(
                    icon = Icons.Rounded.Terminal,
                    accent = termuxStatusColor(setupState.issue),
                    title = stringResource(R.string.settings_termux),
                    body = termuxStatusSentence(setupState),
                )
                when (setupState.issue) {
                    TermuxSetupIssue.Ready -> TourActionRow(
                        primaryLabel = stringResource(R.string.common_continue),
                        onPrimary = onContinue,
                        secondaryLabel = stringResource(R.string.common_refresh),
                        onSecondary = onRefresh,
                    )

                    TermuxSetupIssue.NotInstalled -> TourActionRow(
                        primaryLabel = stringResource(R.string.common_install),
                        onPrimary = onInstallTermux,
                        secondaryLabel = stringResource(R.string.common_skip),
                        onSecondary = onContinue,
                    )

                    TermuxSetupIssue.PermissionMissing -> {
                        TourActionRow(
                            primaryLabel = stringResource(R.string.common_grant_access),
                            onPrimary = onRequestPermission,
                            secondaryLabel = stringResource(R.string.common_skip),
                            onSecondary = onContinue,
                        )
                        SecondaryTextAction(label = stringResource(R.string.onboarding_app_settings), onClick = onOpenAppPermissions)
                    }

                    TermuxSetupIssue.ExternalAppsDisabled -> {
                        TourActionRow(
                            primaryLabel = if (setupState.previouslyConfigured) stringResource(R.string.common_open) else stringResource(R.string.onboarding_copy_and_open_termux),
                            onPrimary = if (setupState.previouslyConfigured) onOpenTermux else ::copyTermuxSetupCommandAndOpenTermux,
                            secondaryLabel = stringResource(R.string.common_skip),
                            onSecondary = onContinue,
                        )
                    }

                    TermuxSetupIssue.DispatchFailed -> {
                        TourActionRow(
                            primaryLabel = if (setupState.previouslyConfigured) stringResource(R.string.common_open) else stringResource(R.string.common_open_termux),
                            onPrimary = onOpenTermux,
                            secondaryLabel = stringResource(R.string.common_skip),
                            onSecondary = onContinue,
                        )
                        if (!setupState.previouslyConfigured) {
                            SecondaryTextAction(label = stringResource(R.string.onboarding_copy_setup_command), onClick = ::copyTermuxSetupCommand)
                            SecondaryTextAction(label = stringResource(R.string.onboarding_termux_settings), onClick = onOpenTermuxSettings)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RootSetupPrompt(
    rootSetupState: RootSetupState,
    onUseRoot: () -> Unit,
    onContinueManual: () -> Unit,
    onRootConfigured: () -> Unit,
    onInstallTermux: () -> Unit,
) {

    StepLead(
        icon = Icons.Rounded.VerifiedUser,
        accent = when (rootSetupState.issue) {
            RootSetupIssue.Ready -> TourGreen
            RootSetupIssue.Available,
            RootSetupIssue.Running -> TourBlue
            RootSetupIssue.PermissionDenied,
            RootSetupIssue.TermuxNotInstalled,
            RootSetupIssue.Failed -> TourGold
            RootSetupIssue.Unknown,
            RootSetupIssue.Unavailable -> TourTextSecondary
        },
        title = stringResource(R.string.onboarding_root_shortcut),
        body = rootSetupPromptBody(rootSetupState),
    )

    when (rootSetupState.issue) {
        RootSetupIssue.Running -> {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.dp,
                    color = TourBlue,
                )
                Text(
                    text = stringResource(R.string.onboarding_waiting_root_authorization),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TourTextSecondary,
                )
            }
            SecondaryTextAction(
                label = stringResource(R.string.onboarding_continue_manual_setup),
                onClick = onContinueManual,
            )
        }

        RootSetupIssue.Ready -> TourActionRow(
            primaryLabel = stringResource(R.string.common_continue),
            onPrimary = onRootConfigured,
            secondaryLabel = stringResource(R.string.onboarding_manual_setup),
            onSecondary = onContinueManual,
        )

        RootSetupIssue.TermuxNotInstalled -> TourActionRow(
            primaryLabel = stringResource(R.string.common_install),
            onPrimary = onInstallTermux,
            secondaryLabel = stringResource(R.string.onboarding_continue_manual_setup),
            onSecondary = onContinueManual,
        )

        RootSetupIssue.Available -> {
            TourActionRow(
                primaryLabel = stringResource(R.string.onboarding_use_root_setup),
                onPrimary = onUseRoot,
                secondaryLabel = stringResource(R.string.onboarding_continue_manual_setup),
                onSecondary = onContinueManual,
            )
        }

        RootSetupIssue.Unknown,
        RootSetupIssue.Unavailable,
        RootSetupIssue.PermissionDenied,
        RootSetupIssue.Failed -> TourActionRow(
            primaryLabel = stringResource(R.string.onboarding_use_root_setup),
            onPrimary = onUseRoot,
            secondaryLabel = stringResource(R.string.onboarding_continue_manual_setup),
            onSecondary = onContinueManual,
        )
    }
}

@Composable
private fun rootSetupPromptBody(rootSetupState: RootSetupState): String = when (rootSetupState.issue) {
    RootSetupIssue.Unknown -> stringResource(R.string.onboarding_root_body_unknown)
    RootSetupIssue.Available -> stringResource(R.string.onboarding_root_body_available)
    RootSetupIssue.Running -> stringResource(R.string.onboarding_root_body_running)
    RootSetupIssue.Ready -> stringResource(R.string.onboarding_root_body_ready)
    RootSetupIssue.Unavailable -> stringResource(R.string.onboarding_root_body_unavailable)
    RootSetupIssue.PermissionDenied -> rootSetupState.detail.ifBlank {
        stringResource(R.string.onboarding_root_body_permission_denied)
    }
    RootSetupIssue.TermuxNotInstalled -> stringResource(R.string.onboarding_root_body_termux_not_installed)
    RootSetupIssue.Failed -> rootSetupState.detail.ifBlank {
        stringResource(R.string.onboarding_root_body_failed)
    }
}

@Composable
private fun AgentModeAuthorizationStep(
    stepIndex: Int,
    stepCount: Int,
    initialMethod: AgentModeAuthorizationMethod,
    onBack: () -> Unit,
    onClose: () -> Unit,
    onContinue: (Boolean, AgentModeAuthorizationMethod) -> Unit,
) {

    ConversationStepPage(
        stepIndex = stepIndex,
        stepCount = stepCount,
        message = stringResource(R.string.onboarding_agent_mode_message),
        onBack = onBack,
        topRightLabel = stringResource(R.string.common_close),
        onTopRight = onClose,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            StepLead(
                icon = Icons.Rounded.SmartToy,
                accent = TourGreen,
                title = stringResource(R.string.settings_agent_mode),
                body = stringResource(R.string.onboarding_agent_mode_choose_method),
            )
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                AgentModeStageButton(
                    label = "Shizuku",
                    subtitle = stringResource(R.string.onboarding_agent_mode_shizuku_subtitle),
                    drawableRes = R.drawable.shizuku_mark,
                    onClick = { onContinue(true, AgentModeAuthorizationMethod.Shizuku) },
                )
                AgentModeStageButton(
                    label = "Root",
                    subtitle = stringResource(R.string.onboarding_agent_mode_root_subtitle),
                    drawableRes = R.drawable.root_mark,
                    onClick = { onContinue(true, AgentModeAuthorizationMethod.Root) },
                )
                AgentModeStageButton(
                    label = stringResource(R.string.common_skip),
                    subtitle = stringResource(R.string.onboarding_agent_mode_skip_subtitle),
                    drawableRes = R.drawable.skip_mark,
                    onClick = { onContinue(false, initialMethod) },
                )
            }
        }
    }
}





@Composable
private fun StepLead(
    icon: ImageVector,
    accent: Color,
    title: String,
    body: String,
) {
    OnboardingStepLead(icon = icon, accent = accent, title = title, body = body)
}





@Composable
private fun BrandMarkBadge(
    @DrawableRes drawableRes: Int,
) {
    val imageSize = if (drawableRes == R.drawable.openai_mark) 30.dp else 32.dp
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(id = drawableRes),
            contentDescription = null,
            modifier = Modifier.size(imageSize),
        )
    }
}

@Composable
private fun AgentModeStageButton(
    label: String,
    subtitle: String,
    @DrawableRes drawableRes: Int,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(86.dp),
        shape = RoundedCornerShape(26.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = TourSurface,
            contentColor = TourTextPrimary,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            BrandMarkBadge(drawableRes = drawableRes)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                horizontalAlignment = Alignment.Start,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleMedium,
                    color = TourTextPrimary,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = TourTextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}




@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.tourBringIntoViewOnFocus(): Modifier = composed {
    val requester = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()

    bringIntoViewRequester(requester)
        .onFocusChanged { focusState ->
            if (focusState.isFocused) {
                scope.launch {
                    delay(250)
                    requester.bringIntoView()
                }
            }
        }
}


@Composable
private fun TourActionRow(
    primaryLabel: String,
    onPrimary: () -> Unit,
    secondaryLabel: String,
    onSecondary: () -> Unit,
    primaryEnabled: Boolean = true,
    primaryLoading: Boolean = false,
) {
    OnboardingActionRow(
        primaryLabel = primaryLabel,
        onPrimary = onPrimary,
        secondaryLabel = secondaryLabel,
        onSecondary = onSecondary,
        primaryEnabled = primaryEnabled,
        primaryLoading = primaryLoading,
    )
}

@Composable
private fun SecondaryTextAction(
    label: String,
    onClick: () -> Unit,
    color: Color = TourTextPrimary,
) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = label,
            color = color,
        )
    }
}



internal fun prioritizedModelOptions(
    piProviderId: String?,
    cachedModels: List<String>,
): List<String> {
    return cachedModels
        .map(String::trim)
        .filter(String::isNotBlank)
        .distinctBy { it.lowercase() }
        .sortedByPreferredModelName()
}


@Composable
private fun termuxStatusSentence(setupState: TermuxSetupState): String = when (setupState.issue) {
    TermuxSetupIssue.Ready -> stringResource(R.string.onboarding_termux_status_ready)
    TermuxSetupIssue.NotInstalled -> stringResource(R.string.onboarding_termux_status_not_installed)
    TermuxSetupIssue.PermissionMissing -> stringResource(R.string.onboarding_termux_status_permission_missing)
    TermuxSetupIssue.ExternalAppsDisabled -> {
        if (setupState.previouslyConfigured) {
            stringResource(R.string.onboarding_termux_status_external_apps_disabled_configured)
        } else {
            stringResource(R.string.onboarding_termux_status_external_apps_disabled)
        }
    }
    TermuxSetupIssue.DispatchFailed -> {
        if (setupState.previouslyConfigured) {
            stringResource(R.string.onboarding_termux_status_dispatch_failed_configured)
        } else {
            stringResource(R.string.onboarding_termux_status_dispatch_failed)
        }
    }
}

private fun termuxStatusColor(issue: TermuxSetupIssue): Color = when (issue) {
    TermuxSetupIssue.Ready -> TourGreen
    TermuxSetupIssue.NotInstalled -> TourGold
    TermuxSetupIssue.PermissionMissing -> TourGold
    TermuxSetupIssue.ExternalAppsDisabled -> TourPurple
    TermuxSetupIssue.DispatchFailed -> TourBlue
}
