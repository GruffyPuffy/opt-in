package se.optin

import android.telecom.Call
import android.telecom.CallScreeningService

class CountryCallScreeningService : CallScreeningService() {
    override fun onScreenCall(callDetails: Call.Details) {
        val number = callDetails.handle?.schemeSpecificPart.orEmpty()
        val block = callDetails.callDirection == Call.Details.DIRECTION_INCOMING &&
            !CallRules.isAllowed(this, number)

        if (callDetails.callDirection == Call.Details.DIRECTION_INCOMING) {
            CallRules.recordCall(this, number, block)
        }

        val response = CallResponse.Builder()
            .setDisallowCall(block)
            .setRejectCall(block)
            .setSkipCallLog(false)
            .setSkipNotification(false)
            .build()
        respondToCall(callDetails, response)
    }
}
