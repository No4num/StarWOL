package com.starchen.wol.ui.components

import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.starchen.wol.ui.theme.TextPrimary
import com.starchen.wol.ui.theme.TextSecondary
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 无限循环滚轮：数字首尾相连，中间项大而亮，往两端逐渐变小变暗。
 * 交互沿用系统的手感（惯性 + 吸附）。
 */
@Composable
fun WheelPicker(
    count: Int,
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    itemHeight: Dp = 72.dp,
    visibleItems: Int = 5,
    formatter: (Int) -> String = { "%02d".format(it) }
) {
    val density = LocalDensity.current
    val itemHeightPx = with(density) { itemHeight.toPx() }
    val loops = 2000
    val total = count * loops
    val startIndex = total / 2 - (total / 2) % count + value

    val state = rememberLazyListState(initialFirstVisibleItemIndex = startIndex)
    val fling = rememberSnapFlingBehavior(lazyListState = state)

    val centerFloat by remember {
        derivedStateOf {
            state.firstVisibleItemIndex + state.firstVisibleItemScrollOffset / itemHeightPx
        }
    }
    val centerIndex by remember { derivedStateOf { centerFloat.roundToInt() } }

    // 滚到位后回收一次通知
    LaunchedEffect(centerIndex) {
        val v = ((centerIndex % count) + count) % count
        onValueChange(v)
    }

    Box(
        modifier = modifier
            .height(itemHeight * visibleItems)
            .fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        LazyColumn(
            state = state,
            flingBehavior = fling,
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                vertical = itemHeight * (visibleItems / 2)
            )
        ) {
            items(total) { index ->
                val v = ((index % count) + count) % count
                val dist = abs(index - centerFloat).coerceAtMost(2.6f)
                val scale = 1f - 0.16f * dist
                val alpha = (1f - 0.42f * dist).coerceIn(0.12f, 1f)
                val weight = if (dist < 0.6f) FontWeight.Medium else FontWeight.Normal
                Box(
                    modifier = Modifier
                        .height(itemHeight)
                        .fillMaxWidth()
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        }
                        .alpha(alpha),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = formatter(v),
                        color = if (dist < 0.6f) TextPrimary else TextSecondary,
                        fontSize = 44.sp,
                        fontWeight = weight
                    )
                }
            }
        }
    }
}
