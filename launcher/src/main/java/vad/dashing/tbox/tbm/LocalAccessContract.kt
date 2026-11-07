package vad.dashing.tbox.tbm

import android.net.Uri

object LocalAccessContract {
    const val AUTHORITY = "vad.dashing.tbox.localaccess"
    val AUTHORITY_URI: Uri = Uri.parse("content://$AUTHORITY")

    // Bundle keys returned by ContentResolver.call
    const val KEY_STATUS = "status"
    const val KEY_ACCESS_TOKEN = "accessToken"
    const val KEY_PORT = "port"
    const val KEY_SERVER_ENABLED = "serverEnabled"

    // Status values
    const val STATUS_GRANTED = "granted"
    const val STATUS_PENDING = "pending"
    const val STATUS_CONSENT_REQUIRED = "consent_required"
    const val STATUS_DENIED = "denied"
    const val STATUS_UNAVAILABLE = "unavailable"
    const val STATUS_UNKNOWN_CLIENT = "unknown_client"
    const val STATUS_NOT_SUPPORTED = "not_supported"
}
