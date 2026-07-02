package com.janadhikar.sos

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import com.janadhikar.R

/**
 * Sends the emergency SMS over the cellular control channel — the one channel
 * that works with zero data connectivity. The emergency contact lives in
 * app-private, backup-excluded SharedPreferences; nothing else is stored.
 */
class SmsDispatcher(private val context: Context) : SosController.Dispatcher {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    override fun isConfigured(): Boolean =
        !emergencyContact().isNullOrBlank() &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) ==
            PackageManager.PERMISSION_GRANTED

    override fun dispatch(): Boolean {
        val contact = emergencyContact() ?: return false
        return try {
            val sms = context.getSystemService(SmsManager::class.java)
            val body = context.getString(R.string.sos_message_body)
            sms.sendMultipartTextMessage(contact, null, sms.divideMessage(body), null, null)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun emergencyContact(): String? = prefs.getString(KEY_CONTACT, null)

    fun setEmergencyContact(phoneNumber: String) {
        prefs.edit().putString(KEY_CONTACT, phoneNumber.trim()).apply()
    }

    companion object {
        private const val PREFS = "sos"
        private const val KEY_CONTACT = "emergency_contact"
    }
}
