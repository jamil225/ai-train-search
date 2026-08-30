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
import com.trainsearch.agent.Llm
import com.trainsearch.agent.Summarizer
import com.trainsearch.data.ApiKeyStore
import com.trainsearch.data.AppDatabase
import com.trainsearch.data.ConversationRepository
import com.trainsearch.data.ConvTurn
import com.trainsearch.ui.BoardScreen
import com.trainsearch.ui.BoardViewModel
import com.trainsearch.ui.KeyScreen
import com.trainsearch.ui.TrainSearchTheme
import com.trainsearch.util.AppLogger

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppLogger.init(applicationContext) // first, so nothing below can log before this exists
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
                            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                                val dao = AppDatabase.get(applicationContext).conversationDao()
                                val summarizer = Summarizer(Llm(key))
                                val conversations = ConversationRepository(
                                    dao = dao,
                                    summarizer = { existing, older ->
                                        summarizer.summarize(
                                            existing,
                                            older.map { ConvTurn(it.role, it.content) }
                                        )
                                    }
                                )
                                return BoardViewModel(key, conversations) as T
                            }
                        }
                    )
                    BoardScreen(vm)
                }
            }
        }
    }
}
