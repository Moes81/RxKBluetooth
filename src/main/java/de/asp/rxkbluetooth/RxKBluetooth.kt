package de.asp.rxkbluetooth

import android.app.Activity
import android.bluetooth.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import de.asp.rxkbluetooth.events.AclEvent
import de.asp.rxkbluetooth.events.BondStateEvent
import de.asp.rxkbluetooth.events.ConnectionStateEvent
import de.asp.rxkbluetooth.events.ServiceEvent
import de.asp.rxkbluetooth.exceptions.GetProfileProxyException
import io.reactivex.Observable
import io.reactivex.Single
import java.io.IOException
import java.util.*


class RxKBluetooth(val applicationContext: Context) {

    private val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
    private var bluetoothAdapterStateObservable: Observable<Int>? = null

    /**
     * Return true if Bluetooth is available.
     *
     * @return true if [BluetoothAdapter] is not null, otherwise Bluetooth is
     * not supported on this hardware platform
     */
    val isBluetoothAvailable: Boolean
        get() = bluetoothAdapter != null

    /**
     * Return [true] if Bluetooth is currently enabled and ready for use.
     *
     * Equivalent to:
     * `getBluetoothState() == STATE_ON`
     *
     * Requires [android.Manifest.permission.BLUETOOTH]
     *
     * @return [true] if the local adapter is turned on
     */
    val isBluetoothEnabled: Boolean
        get() = bluetoothAdapter.isEnabled

    /**
     * This will issue a request to enable Bluetooth through the system settings (without stopping
     * your application) via [ACTION_REQUEST_ENABLE] action Intent.
     *
     * @param activity Activity
     * @param requestCode request code
     */
    fun enableBluetooth(activity: Activity, requestCode: Int) {
        if (!bluetoothAdapter.isEnabled) {
            val enableBluetoothIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            activity.startActivityForResult(enableBluetoothIntent, requestCode)
        }
    }

    /**
     * This will issue a request to discover the Bluetooth device through the system settings (without stopping
     * your application) via [ACTION_REQUEST_DISCOVERABLE] action Intent.
     *
     * @param activity Activity
     * @param requestCode request code
     */
    fun ensureDiscoverable(activity: Activity) {
        if (bluetoothAdapter.scanMode != BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
            val discoverableIntent = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE)
            discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300)
            activity.startActivity(discoverableIntent)
        }
    }

    /**
     * Return the set of [BluetoothDevice] objects that are bonded
     * (paired) to the local adapter.
     *
     * If Bluetooth state is not [BluetoothAdapter.STATE_ON], this API
     * will return an empty set. After turning on Bluetooth,
     * wait for [BluetoothAdapter.ACTION_STATE_CHANGED] with [BluetoothAdapter.STATE_ON]
     * to get the updated value.
     *
     * Requires [android.Manifest.permission.BLUETOOTH].
     *
     * @return unmodifiable set of [BluetoothDevice], or null on error
     */
    val bondedDevices: Set<BluetoothDevice>
        get() = bluetoothAdapter.bondedDevices

    /**
     * Start the remote device discovery process.
     *
     * @return [true] on success, false on error
     */
    fun startDiscovery(): Boolean {
        return bluetoothAdapter.startDiscovery()
    }

    /**
     * Return [true] if the local Bluetooth adapter is currently in the device
     * discovery process.
     *
     * @return [true] if discovering
     */
    fun isDiscovering(): Boolean {
        return bluetoothAdapter.isDiscovering
    }

    /**
     * Cancel the current device discovery process.
     *
     * @return [true] on success, false on error
     */
    fun cancelDiscovery(): Boolean {
        return bluetoothAdapter.cancelDiscovery()
    }

    /**
     * Observes, if Bluetooth is enabled/disabled by the user.
     * It does only emit CHANGES! For the initial state call [isBluetoothEnabled].
     * Possible values are:
     * [BluetoothAdapter.STATE_OFF]
     * [BluetoothAdapter.STATE_TURNING_OFF]
     * [BluetoothAdapter.STATE_ON]
     * [BluetoothAdapter.STATE_TURNING_ON]
     *
     * The returned [Observable] supports multicasting.
     *
     * @return [Observable] emitting the BluetoothAdapter status
     */
    fun observeBluetoothAdapterStateChanges(): Observable<Int> {
        val filter = IntentFilter()
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
        return bluetoothAdapterStateObservable ?: Observable.create<Int> { emitter ->
            val broadcastReceiver = object : BroadcastReceiver() {

                override fun onReceive(context: Context, intent: Intent) {
                    val action = intent.action

                    if (action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                        val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                        emitter.onNext(state)
                    }
                }
            }

            applicationContext.registerReceiver(broadcastReceiver, filter)
            emitter.setCancellable { applicationContext.unregisterReceiver(broadcastReceiver) }
        }.share().also { bluetoothAdapterStateObservable = it }
    }

    /**
     * Observes Bluetooth devices found while discovering.
     *
     * @return [Observable] with BluetoothDevice found
     */
    fun observeDevices(): Observable<BluetoothDevice> {
        val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
        return Observable.create<BluetoothDevice> { emitter ->
            val broadcastReceiver = object : BroadcastReceiver() {

                override fun onReceive(context: Context, intent: Intent) {
                    when (intent.action) {
                        BluetoothDevice.ACTION_FOUND -> {
                            val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                            emitter.onNext(device)
                        }
                    }
                }
            }

            applicationContext.registerReceiver(broadcastReceiver, filter)
            emitter.setCancellable { applicationContext.unregisterReceiver(broadcastReceiver) }
        }
    }

    /**
     * Observes scan mode of device. Possible values are:
     * [BluetoothAdapter.SCAN_MODE_NONE],
     * [BluetoothAdapter.SCAN_MODE_CONNECTABLE],
     * [BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE]
     *
     * @return RxJava Observable with scan mode
     */
    fun observeScanMode(): Observable<Int> {
        val filter = IntentFilter()
        filter.addAction(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED)

        return Observable.create { emitter ->
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    emitter.onNext(bluetoothAdapter.scanMode)
                }
            }

            applicationContext.registerReceiver(receiver, filter)
            emitter.setCancellable { applicationContext.unregisterReceiver(receiver) }
        }
    }

    /**
     * Observes connection to specified profile. See also [BluetoothProfile.ServiceListener].
     *
     * @param bluetoothProfile bluetooth profile to connect to. Can be either [ ][BluetoothProfile.HEALTH],[BluetoothProfile.HEADSET], [BluetoothProfile.A2DP],
     * [BluetoothProfile.GATT] or [BluetoothProfile.GATT_SERVER].
     * @return RxJava Observable with [ServiceEvent]
     */
    fun observeBluetoothProfile(bluetoothProfile: Int): Observable<ServiceEvent> {
        return Observable.create { emitter ->
            if (!bluetoothAdapter.getProfileProxy(applicationContext, object : BluetoothProfile.ServiceListener {
                override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                    emitter.onNext(ServiceEvent(ServiceEvent.State.CONNECTED, profile, proxy))
                }

                override fun onServiceDisconnected(profile: Int) {
                    emitter.onNext(ServiceEvent(ServiceEvent.State.DISCONNECTED, profile, null))
                }
            }, bluetoothProfile)) {
                emitter.onError(GetProfileProxyException())
            }
        }
    }

    /**
     * Close the connection of the profile proxy to the Service.
     *
     *  Clients should call this when they are no longer using the proxy obtained from [observeBluetoothProfile].
     *
     * Profile can be one of [BluetoothProfile.HEALTH],[BluetoothProfile.HEADSET],
     * [BluetoothProfile.A2DP], [BluetoothProfile.GATT] or [BluetoothProfile.GATT_SERVER].
     *
     * @param profile the Bluetooth profile
     * @param proxy profile proxy object
     */
    fun closeProfileProxy(profile: Int, proxy: BluetoothProfile) {
        bluetoothAdapter.closeProfileProxy(profile, proxy)
    }

    /**
     * Observes connection state of devices.
     *
     * @return [Observable] with [ConnectionStateEvent]
     */
    fun observeConnectionState(): Observable<ConnectionStateEvent> {
        val filter = IntentFilter()
        //filter.addAction(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED)

        return Observable.create { emitter ->
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    val status = intent.getIntExtra(BluetoothAdapter.EXTRA_CONNECTION_STATE,
                        BluetoothAdapter.STATE_DISCONNECTED)
                    val previousStatus = intent.getIntExtra(BluetoothAdapter.EXTRA_PREVIOUS_CONNECTION_STATE,
                        BluetoothAdapter.STATE_DISCONNECTED)
                    val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)

                    emitter.onNext(ConnectionStateEvent(status, previousStatus, device))
                }
            }

            applicationContext.registerReceiver(receiver, filter)
            emitter.setCancellable { applicationContext.unregisterReceiver(receiver) }
        }
    }

    /**
     * Observes bond state of devices.
     *
     * @return [Observable] with [BondStateEvent]
     */
    fun observeBondState(): Observable<BondStateEvent> {
        val filter = IntentFilter()
        filter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)

        return Observable.create { emitter ->
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    val state = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE)
                    val previousState = intent.getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE,
                        BluetoothDevice.BOND_NONE)
                    val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)

                    emitter.onNext(BondStateEvent(state, previousState, device))
                }
            }

            applicationContext.registerReceiver(receiver, filter)
            emitter.setCancellable { applicationContext.unregisterReceiver(receiver) }
        }
    }

    /**
     * Opens [BluetoothServerSocket], listens for a single connection request, releases socket
     * and returns a connected [BluetoothSocket] on successful connection. Notifies observers
     * with [IOException] `onError()`.
     *
     * @param name service name for SDP record
     * @param uuid uuid for SDP record
     * @return [Single] with connected [BluetoothSocket] on successful connection
     */
    fun observeBluetoothSocket(name: String, uuid: UUID): Single<BluetoothSocket> {
        return Single.create{ emitter ->
            try {
                val bluetoothServerSocket = bluetoothAdapter.listenUsingRfcommWithServiceRecord(name, uuid)
                emitter.onSuccess(bluetoothServerSocket.accept())
                bluetoothServerSocket.close()
            } catch (ioException: IOException) {
                emitter.onError(ioException)
            }
        }
    }

    /**
     * Create connection to [BluetoothDevice] and returns a connected [BluetoothSocket]
     * on successful connection. Notifies observers with [IOException] `onError()`.
     *
     * @param bluetoothDevice bluetooth device to connect
     * @param uuid uuid for SDP record
     * @return [Single] with connected [BluetoothSocket] on successful connection
     */
    fun observeConnectDevice(bluetoothDevice: BluetoothDevice, uuid: UUID): Single<BluetoothSocket> {
        return Single.create { emitter ->
            try {
                val bluetoothSocket = bluetoothDevice.createRfcommSocketToServiceRecord(uuid)
                bluetoothSocket.connect()
                emitter.onSuccess(bluetoothSocket)
            } catch (e: IOException) {
                emitter.onError(e)
            }
        }
    }

    /**
     * Observes ACL broadcast actions from [BluetoothDevice]. Possible broadcast ACL action
     * values are:
     * [BluetoothDevice.ACTION_ACL_CONNECTED],
     * [BluetoothDevice.ACTION_ACL_DISCONNECT_REQUESTED],
     * [BluetoothDevice.ACTION_ACL_DISCONNECTED]
     *
     * @return [Observable] with [AclEvent]
     */
    fun observeAclEvent(): Observable<AclEvent> {
        val filter = IntentFilter()
        filter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
        filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECT_REQUESTED)

        return Observable.create { emitter ->
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    val action = intent.action
                    val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)

                    emitter.onNext(AclEvent(action, device))
                }
            }

            applicationContext.registerReceiver(receiver, filter)
            emitter.setCancellable { applicationContext.unregisterReceiver(receiver) }
        }
    }
}