package com.sanchr.core.designsystem.component

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import kotlin.test.assertFalse
import org.junit.Rule
import org.junit.Test

class ComponentBehaviorTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun gradientButton_click_callbackFires_whenEnabled() {
        var clicked = false
        composeRule.setContent {
            SanchrGradientButton(text = "Continue", onClick = { clicked = true })
        }
        composeRule.onNodeWithText("Continue").performClick()
        assert(clicked) { "click callback should fire when enabled" }
    }

    @Test
    fun gradientButton_click_doesNotFire_whenDisabled() {
        var clicked = false
        composeRule.setContent {
            SanchrGradientButton(
                text = "Continue",
                enabled = false,
                onClick = { clicked = true },
            )
        }
        composeRule.onNodeWithText("Continue").performClick()
        assertFalse(clicked, "click callback must not fire when disabled")
    }

    @Test
    fun gradientButton_loading_replacesContentWithSpinner() {
        composeRule.setContent {
            SanchrGradientButton(text = "Continue", isLoading = true, onClick = {})
        }
        composeRule.onNodeWithText("Continue").assertDoesNotExist()
    }

    @Test
    fun stepEyebrow_rendersText() {
        composeRule.setContent {
            SanchrStepEyebrow("STEP 1 OF 3")
        }
        composeRule.onNodeWithText("STEP 1 OF 3").assertIsDisplayed()
    }
}
