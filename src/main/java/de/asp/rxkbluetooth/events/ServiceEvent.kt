package de.asp.rxkbluetooth.events

import android.bluetooth.BluetoothProfile

data class ServiceEvent(val state: State, val profileType: Int, val bluetoothProfile: BluetoothProfile?) {

    enum class State {
        CONNECTED,
        DISCONNECTED
    }

    override fun toString(): String {
        return "ServiceEvent{ mState=$state, mProfileType=$profileType, mBluetoothProfile=$bluetoothProfile }"
    }
}