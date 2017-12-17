package de.asp.rxkbluetooth.exceptions

import java.io.IOException

data class ConnectionClosedException(override val message: String = "Connection is closed.")
    : IOException(message) {
}