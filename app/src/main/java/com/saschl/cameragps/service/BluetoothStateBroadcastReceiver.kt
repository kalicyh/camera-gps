package com.saschl.cameragps.service

import android.content.BroadcastReceiver

class BluetoothStateBroadcastReceiver(private val onBluetoothStateChanged: (Boolean) -> Unit) :
    BroadcastReceiver() {
    override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
        if (intent?.action == android.bluetooth.BluetoothAdapter.ACTION_STATE_CHANGED) {
            val state = intent.getIntExtra(
                android.bluetooth.BluetoothAdapter.EXTRA_STATE,
                android.bluetooth.BluetoothAdapter.ERROR
            )
            when (state) {
                android.bluetooth.BluetoothAdapter.STATE_ON -> onBluetoothStateChanged(true)
                android.bluetooth.BluetoothAdapter.STATE_OFF -> onBluetoothStateChanged(false)
            }
        }
    }
}