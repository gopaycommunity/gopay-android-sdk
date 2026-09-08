package com.gopay.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gopay.example.checkout.CheckoutTheme
import com.gopay.example.checkout.SurfaceCard
import com.gopay.example.ui.theme.ExampleAppTheme
import cz.gopay.sdk.GopaySDK

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ExampleAppTheme(dynamicColor = false) {
                MainMenuScreen()
            }
        }
    }
}

/**
 * Entry point of the demo app: pick which face of the SDK you want to see.
 *
 *  * Demo checkout — a realistic e-shop checkout exercising every payment method.
 *  * Developer sandbox — the raw call-by-call console with response dumps.
 */
@Composable
fun MainMenuScreen() {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CheckoutTheme.canvas)
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(CheckoutTheme.gutter)
            .padding(top = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Column {
            Text(
                text = "GoPay SDK",
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                color = CheckoutTheme.ink
            )
            Text(
                text = "Android demo · version ${GopaySDK.version}",
                fontSize = 14.sp,
                color = CheckoutTheme.inkMuted
            )
        }

        Spacer(Modifier.size(6.dp))

        DestinationCard(
            icon = Icons.Filled.ShoppingBag,
            title = "Demo checkout",
            subtitle = "A realistic e-shop checkout — card form, Google Pay, saved card, bank " +
                "transfer and 3DS, end to end.",
            accent = CheckoutTheme.accent
        ) {
            context.startActivity(Intent(context, CheckoutDemoActivity::class.java))
        }

        DestinationCard(
            icon = Icons.Filled.Terminal,
            title = "Developer sandbox",
            subtitle = "Call each SDK method on its own and inspect the raw responses the gateway returns.",
            accent = CheckoutTheme.ink
        ) {
            context.startActivity(Intent(context, SDKTestActivity::class.java))
        }

        Spacer(Modifier.weight(1f))

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            EnvironmentBadge()
            Text(
                text = "Both surfaces talk to this gateway and share DemoConfig.",
                fontSize = 12.sp,
                color = CheckoutTheme.inkMuted,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Which gateway both demo surfaces are pointed at, and a tap target to switch it. Reads
 * [DemoConfig.environment], the same state [DemoConfig.select] updates.
 */
@Composable
private fun EnvironmentBadge() {
    var expanded by remember { mutableStateOf(false) }
    val environment = DemoConfig.environment

    val name = when (environment) {
        DemoEnvironment.DEVELOPMENT -> "DEVELOPMENT"
        DemoEnvironment.SANDBOX -> "SANDBOX"
        DemoEnvironment.PRODUCTION -> "PRODUCTION"
    }
    val tint = when (environment) {
        DemoEnvironment.DEVELOPMENT -> CheckoutTheme.accent
        DemoEnvironment.SANDBOX -> CheckoutTheme.warning
        DemoEnvironment.PRODUCTION -> CheckoutTheme.danger
    }
    val host = runCatching { java.net.URI(environment.sdkEnvironment.apiBaseUrl).host }.getOrNull()

    Box {
        Row(
            modifier = Modifier
                .background(tint.copy(alpha = 0.12f), CircleShape)
                .clickable { expanded = true }
                .padding(horizontal = 11.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(7.dp).background(tint, CircleShape))
            Spacer(Modifier.width(7.dp))
            Text(text = name, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = tint)
            if (host != null) {
                Spacer(Modifier.width(6.dp))
                Text(
                    text = host,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    color = CheckoutTheme.inkMuted
                )
            }
            Spacer(Modifier.width(6.dp))
            Icon(
                Icons.Filled.ArrowDropDown,
                contentDescription = "Switch environment",
                tint = CheckoutTheme.inkMuted,
                modifier = Modifier.size(16.dp)
            )
        }

        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DemoConfig.availableEnvironments.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.title) },
                    onClick = {
                        DemoConfig.select(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun DestinationCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    accent: Color,
    onClick: () -> Unit
) {
    SurfaceCard(
        modifier = Modifier.clickable(onClick = onClick),
        padding = 18.dp
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(accent.copy(alpha = 0.14f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(21.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = CheckoutTheme.ink)
                Spacer(Modifier.size(4.dp))
                Text(subtitle, fontSize = 13.sp, color = CheckoutTheme.inkMuted)
            }
            Spacer(Modifier.width(8.dp))
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = CheckoutTheme.inkMuted,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
