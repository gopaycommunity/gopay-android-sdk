package com.gopay.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.gopay.example.ui.CheckoutScreen
import com.gopay.example.ui.theme.ExampleAppTheme
import com.gopay.sdk.GopaySDK

class MainActivity : ComponentActivity() {
    // Instance of our SDK
    private val gopaySDK = GopaySDK.getInstance()
    
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

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Composable
fun SdkGreeting(modifier: Modifier = Modifier) {
    // Get the greeting from the SDK
    val greeting = GopaySDK.getInstance().helloWorld("App User")
    Text(
        text = greeting,
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    ExampleAppTheme {
        SdkGreeting()
    }
}