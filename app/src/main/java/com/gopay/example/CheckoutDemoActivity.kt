package com.gopay.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import com.gopay.example.checkout.CheckoutHolder
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.gopay.example.checkout.CheckoutScreen
import com.gopay.example.ui.theme.ExampleAppTheme

class CheckoutDemoActivity : ComponentActivity() {

    /** The checkout's state and its running payment, which a rotation must not take away. */
    private val checkout: CheckoutHolder by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // dynamicColor is off so the shop keeps its brand palette on Android 12+.
            ExampleAppTheme(dynamicColor = false) {
                CheckoutScreen(holder = checkout, onClose = { finish() })
            }
        }
    }
}
