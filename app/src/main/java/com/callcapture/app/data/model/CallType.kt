package com.callcapture.app.data.model

enum class CallType(val displayName: String, val packageName: String?) {
    CELLULAR("Phone Call", null),
    WHATSAPP("WhatsApp", "com.whatsapp"),
    WHATSAPP_BUSINESS("WhatsApp Business", "com.whatsapp.w4b"),
    MESSENGER("Messenger", "com.facebook.orca"),
    TELEGRAM("Telegram", "org.telegram.messenger"),
    SIGNAL("Signal", "org.thoughtcrime.securesms"),
    VIBER("Viber", "com.viber.voip"),
    SKYPE("Skype", "com.skype.raider"),
    GOOGLE_MEET("Google Meet", "com.google.android.apps.tachyon"),
    OTHER_VOIP("VoIP Call", null);

    companion object {
        fun fromPackageName(pkg: String?): CallType {
            if (pkg == null) return OTHER_VOIP
            val lower = pkg.lowercase()
            if (lower.contains("orca") || lower.contains("mlite") ||
                (lower.contains("facebook") && !lower.contains("system"))) {
                return MESSENGER
            }
            if (lower == "com.whatsapp.w4b") return WHATSAPP_BUSINESS
            if (lower.contains("whatsapp")) return WHATSAPP
            if (lower.contains("telegram")) return TELEGRAM
            if (lower.contains("securesms")) return SIGNAL
            if (lower.contains("viber")) return VIBER
            if (lower.contains("skype")) return SKYPE
            if (lower.contains("tachyon") || lower.contains("meetings")) return GOOGLE_MEET
            return entries.firstOrNull { it.packageName == pkg } ?: OTHER_VOIP
        }
    }
}
