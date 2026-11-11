package com.medocmaman.data.local

import android.content.Context
import android.content.SharedPreferences

class AppPreferences(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("medoc_maman_prefs", Context.MODE_PRIVATE)

    var motherFirstName: String
        get() = prefs.getString(KEY_MOTHER_NAME, "Maman") ?: "Maman"
        set(value) = prefs.edit().putString(KEY_MOTHER_NAME, value.trim()).apply()

    var motherAddress: String
        get() = prefs.getString(KEY_MOTHER_ADDRESS, "Jérusalem, Israël (Adresse à compléter)") ?: ""
        set(value) = prefs.edit().putString(KEY_MOTHER_ADDRESS, value.trim()).apply()

    var sonPhoneNumber: String
        get() = prefs.getString(KEY_SON_PHONE, "+972500000001") ?: "+972500000001"
        set(value) = prefs.edit().putString(KEY_SON_PHONE, value.trim()).apply()

    var daughterPhoneNumber: String
        get() = prefs.getString(KEY_DAUGHTER_PHONE, "+972500000002") ?: "+972500000002"
        set(value) = prefs.edit().putString(KEY_DAUGHTER_PHONE, value.trim()).apply()

    var caregiverName: String
        get() = prefs.getString(KEY_CAREGIVER_NAME, "Fils / Fille") ?: "Fils / Fille"
        set(value) = prefs.edit().putString(KEY_CAREGIVER_NAME, value.trim()).apply()

    /**
     * true = Vouvoiement ("L'avez-vous déjà pris ?") - Default per spec
     * false = Tutoiement ("L'as-tu déjà pris ?")
     */
    var useVouvoiement: Boolean
        get() = prefs.getBoolean(KEY_USE_VOUVOIEMENT, true)
        set(value) = prefs.edit().putBoolean(KEY_USE_VOUVOIEMENT, value).apply()

    var appRole: AppRole
        get() {
            val raw = prefs.getString(KEY_APP_ROLE, AppRole.PATIENT.name) ?: AppRole.PATIENT.name
            return runCatching { AppRole.valueOf(raw) }.getOrDefault(AppRole.PATIENT)
        }
        set(value) = prefs.edit().putString(KEY_APP_ROLE, value.name).apply()

    fun caregiverNumbers(): List<String> =
        listOf(sonPhoneNumber, daughterPhoneNumber)
            .map { it.trim() }
            .filter { it.isNotEmpty() }

    companion object {
        private const val KEY_MOTHER_NAME = "mother_first_name"
        private const val KEY_MOTHER_ADDRESS = "mother_address"
        private const val KEY_SON_PHONE = "son_phone_number"
        private const val KEY_DAUGHTER_PHONE = "daughter_phone_number"
        private const val KEY_CAREGIVER_NAME = "caregiver_name"
        private const val KEY_USE_VOUVOIEMENT = "use_vouvoiement"
        private const val KEY_APP_ROLE = "app_role"
    }
}
