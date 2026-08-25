package com.gopay.example.checkout

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cz.gopay.sdk.ui.PaymentCardFormTheme

/**
 * Design tokens for the demo e-shop.
 *
 * Defined in code rather than as Material colour schemes so the whole checkout reads in one place,
 * and so the [PaymentCardFormTheme] handed to the SDK's `PaymentCardForm` visibly derives from the
 * same palette — that's the point of the theming demo. Using explicit colours also keeps the shop
 * on-brand on Android 12+, where `ExampleAppTheme`'s dynamic colour would otherwise repaint it.
 */
object CheckoutTheme {

    /** Brand green — buttons, selection, success. */
    val accent: Color @Composable @ReadOnlyComposable get() = pick(0xFF0B8A4B, 0xFF34D07A)

    /** Text on top of [accent]. */
    val onAccent: Color @Composable @ReadOnlyComposable get() = pick(0xFFFFFFFF, 0xFF07130C)

    /** Primary text. */
    val ink: Color @Composable @ReadOnlyComposable get() = pick(0xFF101418, 0xFFF2F4F7)

    /** Secondary text. */
    val inkMuted: Color @Composable @ReadOnlyComposable get() = pick(0xFF6B7280, 0xFF98A2B3)

    /** Page background. */
    val canvas: Color @Composable @ReadOnlyComposable get() = pick(0xFFF4F5F7, 0xFF0B0D10)

    /** Card / sheet background. */
    val surface: Color @Composable @ReadOnlyComposable get() = pick(0xFFFFFFFF, 0xFF16191E)

    /** Subtle fill for tiles and inputs. */
    val surfaceSunken: Color @Composable @ReadOnlyComposable get() = pick(0xFFF0F1F4, 0xFF1E222A)

    /** Hairline separators and borders. */
    val hairline: Color @Composable @ReadOnlyComposable get() = pick(0xFFE4E7EC, 0xFF2A2F38)

    val danger: Color @Composable @ReadOnlyComposable get() = pick(0xFFD92D20, 0xFFF97066)
    val warning: Color @Composable @ReadOnlyComposable get() = pick(0xFFB54708, 0xFFFDB022)

    val gutter: Dp = 16.dp
    val cardRadius: Dp = 18.dp
    val controlRadius: Dp = 14.dp

    /**
     * Handed to the SDK's `PaymentCardForm` so its inputs sit in the shop's design language rather
     * than the SDK defaults.
     */
    val cardForm: PaymentCardFormTheme
        @Composable get() = PaymentCardFormTheme(
            labelTextStyle = TextStyle(color = inkMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
            inputTextStyle = TextStyle(color = ink, fontSize = 16.sp, fontWeight = FontWeight.Medium),
            placeholderTextStyle = TextStyle(color = inkMuted, fontSize = 16.sp),
            helperTextStyle = TextStyle(color = inkMuted, fontSize = 12.sp),
            errorTextStyle = TextStyle(color = danger, fontSize = 12.sp),
            loadingTextStyle = TextStyle(color = inkMuted, fontSize = 14.sp),
            inputBorderColor = hairline,
            inputErrorBorderColor = danger,
            inputBackgroundColor = surfaceSunken,
            inputBorderWidth = 1.dp,
            inputShape = RoundedCornerShape(controlRadius),
            inputPadding = PaddingValues(14.dp),
            fieldSpacing = 4.dp,
            groupSpacing = 14.dp
        )

    @Composable
    @ReadOnlyComposable
    private fun pick(light: Long, dark: Long): Color =
        if (isSystemInDarkTheme()) Color(dark) else Color(light)
}

/** Full-width primary action. */
@Composable
fun PrimaryButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    background: Color = CheckoutTheme.accent,
    foreground: Color = CheckoutTheme.onAccent,
    leading: @Composable (() -> Unit)? = null,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp),
        shape = RoundedCornerShape(CheckoutTheme.controlRadius),
        colors = ButtonDefaults.buttonColors(
            containerColor = background,
            contentColor = foreground,
            disabledContainerColor = background.copy(alpha = 0.5f),
            disabledContentColor = foreground.copy(alpha = 0.8f)
        )
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(8.dp))
        }
        Text(text = text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Quiet secondary action. */
@Composable
fun SecondaryButton(
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp),
        shape = RoundedCornerShape(CheckoutTheme.controlRadius),
        colors = ButtonDefaults.buttonColors(
            containerColor = CheckoutTheme.surfaceSunken,
            contentColor = CheckoutTheme.ink
        )
    ) {
        Text(text = text, fontSize = 16.sp, fontWeight = FontWeight.Medium)
    }
}

/** A rounded elevated container — the checkout's only structural primitive. */
@Composable
fun SurfaceCard(
    modifier: Modifier = Modifier,
    padding: Dp = CheckoutTheme.gutter,
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(14.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(CheckoutTheme.cardRadius)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(CheckoutTheme.surface, shape)
            .border(1.dp, CheckoutTheme.hairline, shape)
            .padding(padding),
        verticalArrangement = verticalArrangement,
        content = content
    )
}
