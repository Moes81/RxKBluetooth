# RxKBluetooth
A little Android library module for wrapping the Android Bluetooth-Layer with RxKotlin.
This lib is NOT tested! Do not use it for production code!

Any contribution is welcome!

Exaple implementaion of a `DeviceComunicator` class in Kotlin

```kotlin
class DeviceCommunicator(private val bluetoothService: IBluetoothService) {

    private val TAG = "DeviceCommunicator"

    private val connectionStateSubject = BehaviorSubject.create<BluetoothConnectionState>()
    private val dataUpdateSubject = PublishSubject.create<FabricCut>()
    private var socketListeningDisposable: Disposable? = null
    private var dataUpdateDisposable: Disposable? = null

    @Volatile private var connection: BluetoothConnection? = null

    init {
        listenToAclUpdates()
        listenToIncomingConnectionRequests()
    }

    override val bluetoothEnabled: Observable<Boolean>
        get() = bluetoothService.bluetoothAdapterState
            .compose(composeBluetoothAdapterStateChanges())
            .distinctUntilChanged()

    override val isConnected: Boolean
        get() = connection?.socket?.isConnected == true

    override var connectedDevice: BluetoothDevice? = null

    override val pairedDevices: Set<BluetoothDevice>
        get() = bluetoothService.pairedDevices

    override val dataUpdates: Observable<FabricCut>
        get() = dataUpdateSubject as Observable<FabricCut>

    override val connectionStateUpdates: Observable<BluetoothConnectionState>
        get() = connectionStateSubject.distinctUntilChanged() as Observable<BluetoothConnectionState>

    override val missingPermissions: List<String>
        get() = bluetoothService.getMissingPermissions()

    override fun scanForBluetoothDevices(): Observable<BluetoothDevice> = bluetoothService.scanForDevices()

    override fun writeData(obj: Any): Boolean = connection?.send(obj) ?: false

    override fun connect(device: BluetoothDevice): Completable {
        if (missingPermissions.isNotEmpty()) {
            throw BluetoothPermissionNotGrantedException()
        } else {
            return bluetoothService.connectToDevice(device)
                .doOnSuccess { conn ->
                    connectionEstablished(conn)
                }
                .doOnError { error ->
                    Log.e(TAG, "Error while connecting to device ${device.name}", error)
                    connectionStateSubject.onNext(
                        BluetoothConnectionState(BluetoothConnectionState.ConnectionStatus.CONNECTION_ERROR))
                }
                .toCompletable()
        }
    }

    override fun disconnect() {
        connection?.closeConnection()
        dataUpdateDisposable?.dispose()
        connectionStateSubject.onNext(
            BluetoothConnectionState(BluetoothConnectionState.ConnectionStatus.DISCONNECTED)
        )
    }

    private fun connectionEstablished(conn: BluetoothConnection) {
        connection = conn
        listenToDataUpdates()
        connectionStateSubject.onNext(
            BluetoothConnectionState(BluetoothConnectionState.ConnectionStatus.CONNECTED, conn.socket.remoteDevice)
        )
    }

    private fun listenToAclUpdates() {
        bluetoothService.aclEventUpdates().subscribe({ aclEvent ->
            when (aclEvent.action) {
                BluetoothDevice.ACTION_ACL_CONNECTED -> onAclConnectedEvent(aclEvent.bluetoothDevice)
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> onAclDisconnectedEvent(aclEvent.bluetoothDevice)
            }
        }, { error ->
            Log.e(TAG, "Error while listening to ACL updates", error)
        })
    }

    private fun onAclConnectedEvent(device: BluetoothDevice) {
        connectedDevice = device
        connectionStateSubject.onNext(
            BluetoothConnectionState(BluetoothConnectionState.ConnectionStatus.CONNECTED, device)
        )
    }

    private fun onAclDisconnectedEvent(device: BluetoothDevice) {
        if (device == connectedDevice) {
            listenToIncomingConnectionRequests()
            connectionStateSubject.onNext(
                BluetoothConnectionState(BluetoothConnectionState.ConnectionStatus.DISCONNECTED, device)
            )
        }
    }

    private fun listenToIncomingConnectionRequests() {
        if (!bluetoothService.bluetoothAvailable || !bluetoothService.bluetoothEnabled) {
            return
        }
        connectionStateSubject.onNext(
            BluetoothConnectionState(BluetoothConnectionState.ConnectionStatus.WAITING_FOR_CONNECTION)
        )
        socketListeningDisposable = bluetoothService.listenToConnectionRequests("stoffBasarHelper")
            .subscribe({ conn ->
                connectionEstablished(conn)
                socketListeningDisposable?.dispose()
            }, { error ->
                Log.e(TAG, "Error while listening to incoming connecting", error)
                connectionStateSubject.onNext(
                    BluetoothConnectionState(BluetoothConnectionState.ConnectionStatus.CONNECTION_ERROR))
            })
    }

    private fun listenToDataUpdates() {
        connection?.let {
            dataUpdateDisposable = it.observeObjectStream()
                .subscribeOnIO()
                .cast(FabricCut::class.java)
                .subscribe({ data ->
                    dataUpdateSubject.onNext(data)
                }, { error ->
                    Log.e(TAG, "Error while listening to incomming data", error)
                })
        }
    }

    private fun composeBluetoothAdapterStateChanges(): ObservableTransformer<Int, Boolean> {
        return ObservableTransformer<Int, Boolean> { upstream ->
            upstream.map {
                when (it) {
                    BluetoothAdapter.STATE_ON -> true
                    BluetoothAdapter.STATE_OFF -> false
                    else -> false
                }
            }.doOnNext { isEnabled ->
                if (isEnabled) {
                    listenToIncomingConnectionRequests()
                } else {
                    socketListeningDisposable?.dispose()
                }
            }.startWith(bluetoothService.bluetoothEnabled)
        }
    }
}
```
