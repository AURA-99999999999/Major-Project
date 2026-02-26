package com.aura.music.auth.validation

object PasswordValidator {
    fun hasMinLength(password: String) = password.length >= 6
    fun hasMaxLength(password: String) = password.length <= 50
    fun hasNumber(password: String) = password.any { it.isDigit() }
    fun hasSpecialChar(password: String) = password.any { !it.isLetterOrDigit() }

    fun isValid(password: String): Boolean {
        return hasMinLength(password) &&
            hasMaxLength(password) &&
            hasNumber(password) &&
            hasSpecialChar(password)
    }
}
