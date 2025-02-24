package com.example.androiddiffusion.ui.components

import android.content.Intent
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext

/**
 * A developer menu component that provides access to developer tools and tests.
 */
@Composable
fun DeveloperMenu(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }
    
    Box(modifier = modifier) {
        IconButton(onClick = { showMenu = true }) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "Developer Menu"
            )
        }
        
        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false }
        ) {
            DropdownMenuItem(
                text = { Text("IntelliSense Test") },
                onClick = {
                    showMenu = false
                    val intent = Intent(context, Class.forName("com.example.androiddiffusion.IntelliSenseTestActivity"))
                    context.startActivity(intent)
                }
            )
            
            DropdownMenuItem(
                text = { Text("Memory Debug") },
                onClick = {
                    showMenu = false
                    // TODO: Implement memory debug
                }
            )
            
            DropdownMenuItem(
                text = { Text("Performance Profile") },
                onClick = {
                    showMenu = false
                    // TODO: Implement performance profile
                }
            )
        }
    }
} 