package de.asp.rxkbluetooth

import android.bluetooth.BluetoothSocket
import android.util.Log
import de.asp.rxkbluetooth.exceptions.ConnectionClosedException
import io.reactivex.BackpressureStrategy
import io.reactivex.Flowable
import io.reactivex.FlowableOnSubscribe
import io.reactivex.FlowableOperator
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription
import java.io.*
import javax.security.auth.login.LoginException

/**
 * Container for simplifying read and write from/to [BluetoothSocket].
 *
 * @param socket bluetooth socket
 * @throws Exception if can't get input/output stream from the socket
 */
class BluetoothConnection(val socket: BluetoothSocket) {

    private val TAG = BluetoothConnection::class.java.name

    private val inputStream: ObjectInputStream?
    private val outputStream: ObjectOutputStream?

    private var observeByteInputStream: Flowable<Byte>? = null
    private var observeObjectInputStream: Flowable<Any>? = null

    private var connected = false

    init {
        try {
            // First creat the output stream, then flush it, THEN create the input stream
            // https://stackoverflow.com/questions/5605153/cannot-create-objectinputstream-with-inputstream-for-a-bluetooth-socket-on-the-a
            outputStream = ObjectOutputStream(socket.outputStream)
            outputStream.flush()
            inputStream = ObjectInputStream(socket.inputStream)
            connected = true
        } catch (e: Exception) {
            Log.e(TAG, e.localizedMessage, e)
            throw Exception("Can't get stream from bluetooth socket")
        } finally {
            if (!connected) {
                closeConnection()
            }
        }
    }

    /**
     * Observes byte from bluetooth's [InputStream]. Will be emitted per byte.
     *
     * @return [Flowable] with [Byte]
     */
    fun observeByteStream(): Flowable<Byte> {
        return observeByteInputStream ?: Flowable.create(FlowableOnSubscribe<Byte> { subscriber ->
            while (!subscriber.isCancelled) {
                try {
                    inputStream?.let {
                        val read = inputStream.read()
                        subscriber.onNext(read.toByte())
                    }
                } catch (e: Exception) {
                    connected = false
                    if (e is IOException) {
                        subscriber.onError(ConnectionClosedException("Can't read stream"))
                    }
                } finally {
                    if (!connected) {
                        closeConnection()
                    }
                }
            }
        }, BackpressureStrategy.BUFFER).share().also { observeByteInputStream = it }
    }

    fun observeObjectStream(): Flowable<Any> {
        return observeObjectInputStream ?: Flowable.create(FlowableOnSubscribe<Any> { subscriber ->
            while (!subscriber.isCancelled) {
                try {
                    inputStream?.let {
                        val readObj = it.readObject()
                        subscriber.onNext(readObj)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Exception while reading from input stream", e)
                    connected = false
                    if (e is IOException) {
                        subscriber.onError(ConnectionClosedException("Can't read stream"))
                    }
                } finally {
                    if (!connected) {
                        closeConnection()
                    }
                }
            }
        }, BackpressureStrategy.BUFFER).share().also { observeObjectInputStream = it }
    }

    /**
     * Observes string from bluetooth's [InputStream] with '\r' (Carriage Return)
     * and '\n' (New Line) as delimiter.
     *
     * @return RxJava Observable with [String]
     */
    fun observeStringStream(): Flowable<String> {
        return observeStringStream('\r'.toByte(), '\n'.toByte())
    }

    /**
     * Observes string from bluetooth's [InputStream].
     *
     * @param delimiter char(s) used for string delimiter
     * @return RxJava Observable with [String]
     */
    fun observeStringStream(vararg delimiter: Byte): Flowable<String> {
        return observeByteStream().lift(FlowableOperator<String, Byte> { subscriber ->
            object : Subscriber<Byte> {
                internal var buffer: ArrayList<Byte> = ArrayList()

                override fun onSubscribe(d: Subscription) {
                    subscriber.onSubscribe(d)
                }

                override fun onComplete() {
                    if (!buffer.isEmpty()) {
                        emit()
                    }
                    subscriber.onComplete()
                }

                override fun onError(e: Throwable) {
                    if (!buffer.isEmpty()) {
                        emit()
                    }
                    subscriber.onError(e)
                }

                override fun onNext(byte: Byte?) {
                    byte?.let {
                        if (delimiter.isEmpty() || delimiter.contains(it)) {
                            emit()
                        } else {
                            buffer.add(it)
                        }
                    }
                }

                private fun emit() {
                    if (buffer.isEmpty()) {
                        subscriber.onNext("")
                        return
                    }

                    val bArray = ByteArray(buffer.size)

                    for (i in 0 until buffer.size) {
                        bArray[i] = buffer[i]
                    }

                    subscriber.onNext(String(bArray))
                    buffer.clear()
                }
            }
        }).onBackpressureBuffer()
    }

    /**
     * Send one byte to bluetooth output stream.
     *
     * @param oneByte a byte
     * @return true if success, false if there was error occurred or disconnected
     */
    fun send(oneByte: Byte): Boolean {
        return send(byteArrayOf(oneByte))
    }

    /**
     * Send array of bytes to bluetooth output stream.
     *
     * @param bytes data to send
     * @return true if success, false if there was error occurred or disconnected
     */
    fun send(bytes: ByteArray): Boolean {
        if (!connected) return false

        return try {
            outputStream?.write(bytes)
            outputStream?.flush()
            true
        } catch (e: IOException) {
            // Error occurred. Better to close terminate the connection
            connected = false
            Log.e(TAG, "Fail to send data")
            false
        } finally {
            if (!connected) {
                closeConnection()
            }
        }
    }

    /**
     * Sends an Any object to the bluetooth output stream.
     *
     * @param  obj the Object to send
     * @return true if success, false if there was error occurred or disconnected
     */
    fun send(obj: Any): Boolean {
        if (!connected) return false

        return try {
            outputStream?.writeObject(obj)
            outputStream?.flush()
            true
        } catch (e: IOException) {
            // Error occurred. Better to close terminate the connection
            connected = false
            Log.e(TAG, "Fail to send data")
            false
        } finally {
            if (!connected) {
                closeConnection()
            }
        }
    }

    /**
     * Send string of text to bluetooth output stream.
     *
     * @param text text to send
     * @return true if success, false if there was error occurred or disconnected
     */
    fun send(text: String): Boolean {
        val sBytes = text.toByteArray()
        return send(sBytes)
    }

    /**
     * Close the streams and socket connection.
     */
    fun closeConnection() {
        try {
            observeByteInputStream = null
            connected = false

            inputStream?.close()
            outputStream?.close()

            socket.close()
        } catch (exeption: Exception) {
            // ignored
        }

    }

}