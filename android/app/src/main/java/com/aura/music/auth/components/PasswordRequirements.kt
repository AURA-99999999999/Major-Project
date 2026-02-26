package com.aura.music.auth.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.aura.music.auth.validation.PasswordValidator

@Composable
fun PasswordRequirements(password: String, modifier: Modifier = Modifier) {
    @Composable
    fun Requirement(text: String, satisfied: Boolean) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            Icon(
                imageVector = if (satisfied) Icons.Filled.Check else Icons.Filled.Close,
                contentDescription = null,
                tint = if (satisfied) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(text, style = MaterialTheme.typography.bodySmall)
        }
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Requirement(
            text = "6-50 characters",
            satisfied = PasswordValidator.hasMinLength(password) && PasswordValidator.hasMaxLength(password)
        )
        Requirement(
            text = "At least one number",
            satisfied = PasswordValidator.hasNumber(password)
        )
        Requirement(
            text = "At least one special character",
            satisfied = PasswordValidator.hasSpecialChar(password)
        )
    }
}
