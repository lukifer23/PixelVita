package com.example.androiddiffusion.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.androiddiffusion.config.QuantizationType
import com.example.androiddiffusion.viewmodel.MainViewModel

@Composable
fun MainScreen(mainViewModel: MainViewModel = viewModel()) {
    val models by mainViewModel.models.collectAsState()
    var modelMenuExpanded = remember { mutableStateOf(false) }
    var quantMenuExpanded = remember { mutableStateOf(false) }
    var selectedModelName = remember { mutableStateOf("Select Model") }
    var selectedQuant = remember { mutableStateOf(QuantizationType.INT8) }

    Column(modifier = Modifier.padding(16.dp)) {
        Box {
            TextButton(onClick = { modelMenuExpanded.value = true }) {
                Text(selectedModelName.value)
            }
            DropdownMenu(expanded = modelMenuExpanded.value, onDismissRequest = { modelMenuExpanded.value = false }) {
                models.forEach { model ->
                    DropdownMenuItem(
                        text = { Text(model.name) },
                        onClick = {
                            selectedModelName.value = model.name
                            modelMenuExpanded.value = false
                            mainViewModel.selectModel(model)
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Box {
            TextButton(onClick = { quantMenuExpanded.value = true }) {
                Text(selectedQuant.value.name)
            }
            DropdownMenu(expanded = quantMenuExpanded.value, onDismissRequest = { quantMenuExpanded.value = false }) {
                QuantizationType.values().forEach { q ->
                    DropdownMenuItem(
                        text = { Text(q.name) },
                        onClick = {
                            selectedQuant.value = q
                            quantMenuExpanded.value = false
                            mainViewModel.selectQuantization(q)
                        }
                    )
                }
            }
        }
    }
}
