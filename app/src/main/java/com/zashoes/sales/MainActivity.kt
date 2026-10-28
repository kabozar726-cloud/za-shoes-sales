package com.zashoes.sales

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModelProvider

// ZA Shoes brand theme: deep green + gold on a warm cream background.
private val ZaGreen = Color(0xFF1B5E20)
private val ZaGold = Color(0xFFC9A227)
private val ZaCream = Color(0xFFFFF8E1)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val vm = ViewModelProvider(this)[ShopViewModel::class.java]
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = ZaGreen,
                    secondary = ZaGold,
                    background = ZaCream,
                    surface = Color.White,
                    onPrimary = Color.White,
                    primaryContainer = Color(0xFFE8F5E9)
                )
            ) {
                Surface {
                    ShopApp(vm)
                }
            }
        }
    }
}
