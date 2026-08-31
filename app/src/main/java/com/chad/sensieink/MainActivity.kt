package com.chad.sensieink

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chad.sensieink.data.TokenStore
import com.chad.sensieink.ui.MainViewModel
import com.chad.sensieink.ui.SensiApp
import com.mudita.mmd.ThemeMMD

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val tokenStore = TokenStore(applicationContext)

        setContent {
            val viewModel: MainViewModel = viewModel(factory = MainViewModel.factory(tokenStore))
            ThemeMMD {
                SensiApp(viewModel = viewModel)
            }
        }
    }
}
