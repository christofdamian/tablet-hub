package net.damian.tablethub.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.damian.tablethub.ui.components.PageIndicator
import net.damian.tablethub.ui.screens.buttons.ButtonsScreen
import net.damian.tablethub.ui.screens.clock.ClockScreen
import net.damian.tablethub.ui.screens.player.PlayerScreen

enum class Screen {
    Clock,
    Buttons,
    Player
}

@Composable
fun AppNavigation(
    modifier: Modifier = Modifier,
    isNightModeActive: Boolean = false,
    onNightModeToggle: () -> Unit = {},
    onSettingsClick: () -> Unit = {}
) {
    val screens = Screen.entries
    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { screens.size }
    )

    Box(modifier = modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            when (screens[page]) {
                Screen.Clock -> ClockScreen(
                    isNightModeActive = isNightModeActive,
                    onNightModeToggle = onNightModeToggle,
                    onSettingsClick = onSettingsClick
                )
                Screen.Buttons -> ButtonsScreen()
                Screen.Player -> PlayerScreen()
            }
        }

        PageIndicator(
            pageCount = screens.size,
            currentPage = pagerState.currentPage,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp)
        )
    }
}
