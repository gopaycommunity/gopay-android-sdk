package com.gopay.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.gopay.example.ui.CheckoutScreen
import com.gopay.example.ui.theme.ExampleAppTheme
import com.gopay.sdk.GopaySDK

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ExampleAppTheme {
                // Display the checkout screen
                CheckoutScreen()
            }
        }
    }
}