package com.example.androiddiffusion.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.androiddiffusion.config.ModelConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvancedSettings(
    modifier: Modifier = Modifier,
    guidanceScale: Float,
    onGuidanceScaleChange: (Float) -> Unit,
    steps: Int = ModelConfig.InferenceSettings.DEFAULT_STEPS,
    onStepsChange: (Int) -> Unit,
    samplingMethod: String,
    onSamplingMethodChange: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var samplingMenuExpanded by remember { mutableStateOf(false) }
    val samplingMethods = listOf("DDIM", "PNDM", "DPM-Solver++")
    
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Advanced Settings",
                style = MaterialTheme.typography.titleMedium
            )
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (expanded) "Collapse" else "Expand"
            )
        }
        
        AnimatedVisibility(visible = expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Steps
                OutlinedTextField(
                    value = steps.toString(),
                    onValueChange = { value ->
                        value.toIntOrNull()?.let { 
                            if (it in 1..ModelConfig.InferenceSettings.MAX_STEPS) {
                                onStepsChange(it)
                            }
                        }
                    },
                    label = { Text("Steps (1-${ModelConfig.InferenceSettings.MAX_STEPS})") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                
                // Guidance Scale
                OutlinedTextField(
                    value = guidanceScale.toString(),
                    onValueChange = { value ->
                        value.toFloatOrNull()?.let { 
                            if (it in 1f..20f) onGuidanceScaleChange(it)
                        }
                    },
                    label = { Text("Guidance Scale (1-20)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                
                // Sampling Method
                ExposedDropdownMenuBox(
                    expanded = samplingMenuExpanded,
                    onExpandedChange = { samplingMenuExpanded = it },
                ) {
                    OutlinedTextField(
                        value = samplingMethod,
                        onValueChange = { },
                        readOnly = true,
                        label = { Text("Sampling Method") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = samplingMenuExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    
                    ExposedDropdownMenu(
                        expanded = samplingMenuExpanded,
                        onDismissRequest = { samplingMenuExpanded = false }
                    ) {
                        samplingMethods.forEach { method ->
                            DropdownMenuItem(
                                text = { Text(method) },
                                onClick = {
                                    onSamplingMethodChange(method)
                                    samplingMenuExpanded = false
                                }
                            )
                        }
                    }
                }

                // Image Size
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Image Size: ${ModelConfig.InferenceSettings.DEFAULT_IMAGE_SIZE}x${ModelConfig.InferenceSettings.DEFAULT_IMAGE_HEIGHT}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
} 