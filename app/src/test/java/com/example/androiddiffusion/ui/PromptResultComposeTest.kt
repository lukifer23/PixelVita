package com.example.androiddiffusion.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.*
import org.junit.Rule
import org.junit.Test

class PromptResultComposeTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun promptEntryAndResultDisplay() {
        composeTestRule.setContent {
            var text by remember { mutableStateOf("") }
            var showResult by remember { mutableStateOf(false) }
            Column {
                TextField(value = text, onValueChange = { text = it },
                    modifier = Modifier.testTag("promptInput"))
                Button(onClick = { showResult = true },
                    modifier = Modifier.testTag("generateButton")) {
                    Text("Generate")
                }
                if (showResult) {
                    Text("Result", modifier = Modifier.testTag("resultDisplay"))
                }
            }
        }

        composeTestRule.onNodeWithTag("promptInput").performTextInput("Hi")
        composeTestRule.onNodeWithTag("generateButton").performClick()
        composeTestRule.onNodeWithTag("resultDisplay").assertExists()
    }
}
