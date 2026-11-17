package se.optin

import android.telecom.Call
import android.telecom.CallScreeningService
import android.util.Log

class CountryCallScreeningService : CallScreeningService() {
    override fun onScreenCall(callDetails: Call.Details) {
        val number = callDetails.handle?.schemeSpecificPart.orEmpty()
        val block = callDetails.callDirection == Call.Details.DIRECTION_INCOMING &&
            !CallRules.isAllowed(this, number)

        if (callDetails.callDirection == Call.Details.DIRECTION_INCOMING) {
            val eventSaved = CallRules.recordCall(this, number, block)
            val outcome = if (block) "BLOCKED" else "ALLOWED"
            CallRules.recordScreeningCheck(
                this,
                direction = "INCOMING",
                outcome = outcome,
                numberPresent = number.isNotBlank(),
                activitySaved = eventSaved
            )
            Log.i(TAG, "incoming call screened: outcome=$outcome numberPresent=${number.isNotBlank()} activitySaved=$eventSaved")
        }

        val response = CallResponse.Builder()
            .setDisallowCall(block)
            .setRejectCall(block)
            .setSkipCallLog(false)
            .setSkipNotification(false)
            .build()
        respondToCall(callDetails, response)
    }

    companion object {
        private const val TAG = "OptInScreening"
    }
}
