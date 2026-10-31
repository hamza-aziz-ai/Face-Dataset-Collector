package com.example.facedatasetcollector

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import com.example.facedatasetcollector.ui.AppRoot
import com.example.facedatasetcollector.ui.EnrolmentViewModel
import com.example.facedatasetcollector.ui.theme.FaceDatasetCollectorTheme

class MainActivity : ComponentActivity() {
    private val model: EnrolmentViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FaceDatasetCollectorTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) {
                    innerPadding ->
                    AppRoot(model, Modifier.fillMaxSize().padding(innerPadding))
                }
            }
        }
    }
}
