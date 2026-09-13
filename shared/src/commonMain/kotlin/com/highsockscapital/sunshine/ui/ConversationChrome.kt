package com.highsockscapital.sunshine.ui

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.highsockscapital.sunshine.ui.theme.SunshineOnSurface
import com.highsockscapital.sunshine.ui.theme.SunshineOutline
import com.highsockscapital.sunshine.platform.LocalReduceMotion
import com.highsockscapital.sunshine.ui.theme.SunshineSurface

private val ConversationMotionEasing = CubicBezierEasing(0.22f, 0.84f, 0.18f, 1f)

@Composable
fun SunshineConversationTopBarFrame(
    menuDescription: String,
    newChatDescription: String,
    onMenu: () -> Unit,
    onNewChat: () -> Unit,
    showMenu: Boolean = true,
    modifier: Modifier = Modifier,
    centerContent: @Composable BoxScope.() -> Unit,
) {
    val reduceMotion = LocalReduceMotion.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 15.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showMenu) {
            HeaderCircleButton(
                icon = Icons.Rounded.Menu,
                contentDescription = menuDescription,
                onClick = onMenu,
                size = 38.dp,
                iconSize = 19.dp,
                containerColor = SunshineSurface.copy(alpha = 0.96f),
            )
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(
                    start = if (showMenu) 12.dp else 5.dp,
                    end = 12.dp,
                ),
            content = centerContent,
        )
        HeaderCircleButton(
            icon = LucideIcons.SquarePen,
            contentDescription = newChatDescription,
            onClick = onNewChat,
            size = 38.dp,
            iconSize = 19.dp,
            containerColor = SunshineSurface.copy(alpha = 0.96f),
        )
    }
}

@Composable
fun SunshineSimpleModelSelector(
    label: String,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxWidth().height(38.dp)) {
        Row(
            modifier = Modifier.matchParentSize()
                .border(1.dp, SunshineOutline, RoundedCornerShape(999.dp))
                .clip(RoundedCornerShape(999.dp))
                .background(SunshineSurface.copy(alpha = 0.96f))
                .padding(horizontal = 17.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Normal),
                color = SunshineOnSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

