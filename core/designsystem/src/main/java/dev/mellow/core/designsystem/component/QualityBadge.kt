package dev.mellow.core.designsystem.component

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.mellow.core.designsystem.theme.MellowFonts
import dev.mellow.core.designsystem.theme.MellowPalette
import dev.mellow.core.designsystem.theme.MellowTheme

@Composable
fun QualityBadge(
    codec: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = codec.uppercase(),
        style = TextStyle(
            fontFamily = MellowFonts.Mono,
            fontWeight = FontWeight.SemiBold,
            fontSize = 10.sp,
            letterSpacing = 0.5.sp,
        ),
        color = MellowTheme.colors.accentStrong,
        modifier = modifier
            .border(1.dp, MellowPalette.Stone600, RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}
