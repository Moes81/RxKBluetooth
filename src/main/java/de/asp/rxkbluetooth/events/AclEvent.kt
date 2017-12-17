package de.asp.rxkbluetooth.events

import android.bluetooth.BluetoothDevice

/**
 * Event container class.  Contains broadcast ACL action and [BluetoothDevice].
 *
 * Possible broadcast ACL action values are:
 * [BluetoothDevice.ACTION_ACL_CONNECTED],
 * [BluetoothDevice.ACTION_ACL_DISCONNECT_REQUESTED],
 * [BluetoothDevice.ACTION_ACL_DISCONNECTED]
 */
data class AclEvent(val action: String, val bluetoothDevice: BluetoothDevice) {
    override fun toString(): String {
        return "AclEvent{ mAction=$action , mBluetoothDevice=$bluetoothDevice }"
    }
}