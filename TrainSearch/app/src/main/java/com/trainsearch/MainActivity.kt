package com.trainsearch

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.trainsearch.data.ApiKeyStore
import com.trainsearch.ui.BoardScreen
import com.trainsearch.ui.BoardViewModel
import com.trainsearch.ui.KeyScreen
import com.trainsearch.ui.TrainSearchTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = ApiKeyStore(applicationContext)

        setContent {
            TrainSearchTheme {
                var apiKey by remember { mutableStateOf(store.load()) }
                val key = apiKey

                if (key.isNullOrBlank()) {
                    KeyScreen(onSaved = { store.save(it); apiKey = it })
                } else {
                    val vm: BoardViewModel = viewModel(
                        key = "board",
                        factory = object : ViewModelProvider.Factory {
                            @Suppress("UNCHECKED_CAST")
                            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                                BoardViewModel(key) as T
                        }
                    )
                    BoardScreen(vm)
                }
            }
        }
    }
}
