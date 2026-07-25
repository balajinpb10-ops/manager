package com.vaultra.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import com.vaultra.app.ui.VaultraApp
import com.vaultra.app.ui.theme.Bg
import com.vaultra.app.ui.theme.VaultraTheme

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            VaultraTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Bg) {
                    VaultraApp(activity = this)
                }
            }
        }
    }
}
