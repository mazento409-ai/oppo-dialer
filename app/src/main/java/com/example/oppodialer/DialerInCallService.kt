package com.example.oppodialer

import android.content.Intent
import android.telecom.Call
import android.telecom.InCallService

class DialerInCallService : InCallService() {

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        CallManager.add(call)
        val i = Intent(this, InCallActivity::class.java)
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(i)
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        CallManager.remove(call)
    }
}

object CallManager {
    var call: Call? = null
    var onChange: (() -> Unit)? = null

    private val callback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            onChange?.invoke()
        }
    }

    fun add(c: Call) {
        call = c
        c.registerCallback(callback)
        onChange?.invoke()
    }

    fun remove(c: Call) {
        c.unregisterCallback(callback)
        if (call == c) call = null
        onChange?.invoke()
    }

    fun answer() {
        call?.answer(0)
    }

    fun hangup() {
        val c = call ?: return
        if (c.state == Call.STATE_RINGING) {
            c.reject(false, null)
        } else {
            c.disconnect()
        }
    }
}
