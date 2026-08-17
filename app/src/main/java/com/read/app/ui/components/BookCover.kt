package com.read.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

// 柔和的封面渐变色对（低饱和度、护眼），根据书名哈希稳定分配
private val coverGradients = listOf(
    listOf(Color(0xFF8D6E63), Color(0xFF6D4C41)), // 咖啡棕
    listOf(Color(0xFF7986CB), Color(0xFF5C6BC0)), // 雾霾蓝
    listOf(Color(0xFF81C784), Color(0xFF66A169)), // 黛绿
    listOf(Color(0xFFBA8F6C), Color(0xFF9C6B45)), // 赭石
    listOf(Color(0xFF9575CD), Color(0xFF7E57C2)), // 藕荷紫
    listOf(Color(0xFF4DB6AC), Color(0xFF38908A)), // 青瓷
    listOf(Color(0xFFE0A179), Color(0xFFC4824F)), // 杏黄
    listOf(Color(0xFF78909C), Color(0xFF546E7A))  // 黛蓝灰
)

@Composable
fun BookCover(
    title: String,
    modifier: Modifier = Modifier
) {
    val colors = coverGradients[abs(title.hashCode()) % coverGradients.size]

    Box(
        modifier = modifier
            .shadow(4.dp, RoundedCornerShape(6.dp))
            .clip(RoundedCornerShape(6.dp))
            .background(Brush.linearGradient(colors)),
        contentAlignment = Alignment.Center
    ) {
        // 书脊装饰线
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(4.dp)
                .align(Alignment.CenterStart)
                .background(Color.White.copy(alpha = 0.18f))
        )
        Text(
            text = title,
            color = Color.White,
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
            lineHeight = 22.sp,
            textAlign = TextAlign.Center,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp)
        )
    }
}
