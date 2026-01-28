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

// Large page count for infinite scrolling effect
private const val INFINITE_PAGE_COUNT = 10000

@Composable
fun AppNavigation(
    modifier: Modifier = Modifier,
    isNightModeActive: Boolean = false,
    onNightModeToggle: () -> Unit = {},
    onSettingsClick: () -> Unit = {}
) {
    val screens = Screen.entries
    // Start in the middle, aligned to Clock screen (index 0)
    val initialPage = (INFINITE_PAGE_COUNT / 2 / screens.size) * screens.size
    val pagerState = rememberPagerState(
        initialPage = initialPage,
        pageCount = { INFINITE_PAGE_COUNT }
    )

    Box(modifier = modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val screenIndex = page.mod(screens.size)
            when (screens[screenIndex]) {
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
            currentPage = pagerState.currentPage.mod(screens.size),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp)
        )
    }
}
