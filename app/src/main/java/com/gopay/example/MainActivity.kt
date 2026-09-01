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
        // Only on a real launch, never on a mere recreation — see applyLaunchOverrides.
        if (savedInstanceState == null || !DemoConfig.launchOverridesApplied) applyLaunchOverrides()
        enableEdgeToEdge()
        setContent {
            ExampleAppTheme(dynamicColor = false) {
                MainMenuScreen()
            }
        }
    }

    /**
     * Re-points the SDK at whatever gateway the launch intent names — see [DemoLaunchOverrides]
     * for the extras and the `adb` invocation. Runs before `setContent`, so no screen can create a
     * payment session against the environment we're about to leave, and is a no-op on a plain
     * launch.
     *
     * Deliberately skipped on activity recreation: a rotation replays the original intent, and
     * re-applying it would silently drag the environment back to development after someone picked
     * sandbox or production from the badge. A restore after process death also arrives with a
     * non-null `savedInstanceState`, but there `DemoConfig` is a fresh object back at its
     * defaults, so the override genuinely has to be redone — `launchOverridesApplied` is what
     * tells the two apart.
     *
     * Overrides therefore land on a cold start only. `am start` on a task that is already up just
     * brings it to the front without delivering the new extras (this activity is `standard`
     * launchMode, so not even `onNewIntent` fires), which is why the documented invocation uses
     * `am start -S`. That is the semantics you want anyway — re-pointing at another gateway with
     * a live payment session still open would be worse.
     */
    private fun applyLaunchOverrides() {
        val overrides = DemoLaunchOverrides.from(intent::getStringExtra)
        when (val outcome = DemoConfig.applyLaunchOverrides(overrides)) {
            is OverrideOutcome.None -> Unit
            is OverrideOutcome.Applied -> println("⚙️ Launch override applied — gateway ${outcome.baseUrl}")
            is OverrideOutcome.Rejected ->
                println(
                    "\n⛔️ LAUNCH OVERRIDES IGNORED IN FULL — the base URL was unusable, so the " +
                        "credentials that came with it were dropped too rather than sent to the " +
                        "compiled-in gateway. Fix the URL and relaunch.\n   ${outcome.reason}\n"
                )
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
 * [DemoConfig.environment] — the same state [DemoConfig.select] updates — so it's never possible
 * to demo against one environment while the label claims another.
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
    // Sandbox and production resolve to fixed hosts inside the SDK; `apiBaseUrl` is public
    // specifically so this can read them without duplicating the URLs here.
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
            DemoEnvironment.entries.forEach { option ->
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
