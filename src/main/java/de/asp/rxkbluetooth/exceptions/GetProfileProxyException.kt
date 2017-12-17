package de.asp.rxkbluetooth.exceptions

/**
 * Thrown when [BluetoothAdapter.getProfileProxy] returns [true], which means that connection
 * to bluetooth profile failed.
 */
class GetProfileProxyException: RuntimeException("Failed to get profile proxy") {
}