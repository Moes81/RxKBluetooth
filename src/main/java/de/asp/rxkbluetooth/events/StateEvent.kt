package de.asp.rxkbluetooth.events

import android.bluetooth.BluetoothDevice

sealed class StateEvent(open val state: Int, open val previousState: Int, open val bluetoothDevice: BluetoothDevice) {

    override fun toString(): String {
        return "ConnectionStateEvent{" +
            "mState=" + state +
            ", mPreviousState=" + previousState +
            ", mBluetoothDevice=" + bluetoothDevice +
            '}'
    }
}

/**
 * Event container class.  Contains connection state (whether the device is disconnected, connecting, connected,
 * or disconnecting), previous connection state, and [BluetoothDevice].
 *
 * Possible state values are:
 * [BluetoothAdapter.STATE_DISCONNECTED],
 * [BluetoothAdapter.STATE_CONNECTING],
 * [BluetoothAdapter.STATE_CONNECTED],
 * [BluetoothAdapter.STATE_DISCONNECTING]
 */
class ConnectionStateEvent(override val state: Int, override val previousState: Int, override val bluetoothDevice: BluetoothDevice)
    : StateEvent(state, previousState, bluetoothDevice)

/**
 * Event container class.  Contains bond state (whether the device is unbonded, bonding, or bonded),
 * previous bond state, and [BluetoothDevice].
 *
 * Possible state values are:
 * [BluetoothDevice.BOND_NONE],
 * [BluetoothDevice.BOND_BONDING],
 * [BluetoothDevice.BOND_BONDED]
 */
data class BondStateEvent(override val state: Int, override val previousState: Int, override val bluetoothDevice: BluetoothDevice)
    : StateEvent(state, previousState, bluetoothDevice)