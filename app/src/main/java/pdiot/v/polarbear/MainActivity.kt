package pdiot.v.polarbear

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.InternalTextApi
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import pdiot.v.polarbear.bluetooth.BluetoothSpeckService
import pdiot.v.polarbear.bluetooth.ConnectingActivity
import pdiot.v.polarbear.ui.theme.PolarBearTheme
import pdiot.v.polarbear.utils.Constants
import pdiot.v.polarbear.utils.RESpeckLiveData
import pdiot.v.polarbear.utils.ThingyLiveData
import pdiot.v.polarbear.utils.Utils
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val activityTAG = "MainActivity"
    private lateinit var respeckReceiver: BroadcastReceiver
    private lateinit var thingyReceiver: BroadcastReceiver
    private lateinit var respeckLooper: Looper
    private lateinit var thingyLooper: Looper

    private val respeckFilterTest = IntentFilter(Constants.ACTION_RESPECK_LIVE_BROADCAST)
    private val thingyFilterTest = IntentFilter(Constants.ACTION_THINGY_BROADCAST)

    lateinit var respeckLiveData: RESpeckLiveData
    lateinit var thingyLiveData: ThingyLiveData

    var defaultRespeckId = "E7:6E:9C:24:55:9A"
    var defaultThingyId = "DF:80:AA:B3:5A:F7"

    var thingyOn = false
    var respeckOn = false

    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        when {
            permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false) -> {
                // Precise location access granted.
            }
            permissions.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false) -> {
                // Only approximate location access granted.
            } else -> {
            // No location access granted.
        }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)


        val sharedPreferences = getSharedPreferences(Constants.PREFERENCES_FILE, Context.MODE_PRIVATE)

        locationPermissionRequest.launch(arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION))

//        setupBluetoothService()

        sharedPreferences.edit().putString(
            Constants.RESPECK_MAC_ADDRESS_PREF,
            defaultRespeckId
        ).apply()
        sharedPreferences.edit().putInt(Constants.RESPECK_VERSION, 6).apply()

        sharedPreferences.edit().putString(
            Constants.THINGY_MAC_ADDRESS_PREF,
            defaultThingyId
        ).apply()

        startSpeckService()

        Log.d(activityTAG, "onCreate: setting up respeck receiver")
        // register respeck receiver
        respeckReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == Constants.ACTION_RESPECK_LIVE_BROADCAST) {
                    respeckLiveData = intent.getSerializableExtra(Constants.RESPECK_LIVE_DATA) as RESpeckLiveData
                    Log.d("Live", "onReceive: liveData = $respeckLiveData")
                    lifecycleScope.launch {
                        setRespeckAcc(this@MainActivity,
                            "${respeckLiveData.accelX}",
                            "${respeckLiveData.accelY}",
                            "${respeckLiveData.accelZ}")
                        setRespeckGyr(this@MainActivity,
                            "${respeckLiveData.gyro.x}",
                            "${respeckLiveData.gyro.y}",
                            "${respeckLiveData.gyro.z}")
                    }
                    respeckOn = true
                }
            }
        }

        // important to set this on a background thread otherwise it will block the UI
        val respeckHandlerThread = HandlerThread("bgProThreadRespeck")
        respeckHandlerThread.start()
        respeckLooper = respeckHandlerThread.looper
        val respeckHandler = Handler(respeckLooper)
        this.registerReceiver(respeckReceiver, respeckFilterTest, null, respeckHandler)

        Log.d(activityTAG, "onCreate: registering thingy receiver")
        // register thingy receiver
        thingyReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == Constants.ACTION_THINGY_BROADCAST) {
                    thingyLiveData = intent.getSerializableExtra(Constants.THINGY_LIVE_DATA) as ThingyLiveData
                    Log.d("Live", "onReceive: thingyLiveData = $thingyLiveData")

                    lifecycleScope.launch {
                        setThingyAcc(this@MainActivity,
                            "${thingyLiveData.accelX}",
                            "${thingyLiveData.accelY}",
                            "${thingyLiveData.accelZ}")
                        setThingyGyr(this@MainActivity,
                            "${thingyLiveData.gyro.x}",
                            "${thingyLiveData.gyro.y}",
                            "${thingyLiveData.gyro.z}")
                        setThingyMag(this@MainActivity,
                            "${thingyLiveData.mag.x}",
                            "${thingyLiveData.mag.y}",
                            "${thingyLiveData.mag.z}")
                    }
                    thingyOn = true
                }
            }
        }

        // important to set this on a background thread otherwise it will block the UI
        val thingyHandlerThread = HandlerThread("bgProThreadThingy")
        thingyHandlerThread.start()
        thingyLooper = thingyHandlerThread.looper
        val thingyHandler = Handler(thingyLooper)
        this.registerReceiver(thingyReceiver, thingyFilterTest, null, thingyHandler)

        setContent {
            PolarBearTheme {
                // A surface container using the 'background' color from the theme
                Surface(color = MaterialTheme.colors.background) {
                    DeviceIdTextField(defaultRespeckId, defaultThingyId)
                }
            }
        }
    }

//    private fun setupBluetoothService() {
//        val isServiceRunning = Utils.isServiceRunning(BluetoothSpeckService::class.java, applicationContext)
//        Log.i("debug", "isServiceRunning = $isServiceRunning")
//
//        // check sharedPreferences for an existing Respeck id
//        val sharedPreferences = getSharedPreferences(Constants.PREFERENCES_FILE, Context.MODE_PRIVATE)
//        if (sharedPreferences.contains(Constants.RESPECK_MAC_ADDRESS_PREF)) {
//            Log.i("shared-pref", "Already saw a respeckID, starting service and attempting to reconnect")
//
//            // launch service to reconnect
//            // start the bluetooth service if it's not already running
//            if(!isServiceRunning) {
//                Log.i("service", "Starting BLT service")
//                val simpleIntent = Intent(this, BluetoothSpeckService::class.java)
//                this.startService(simpleIntent)
//            }
//        }
//        else {
//            Log.i("shared-pref", "No Respeck seen before, must pair first")
//            // TODO then start the service from the connection activity
//        }
//    }

    private fun startSpeckService() {
        // TODO if it's not already running
        val isServiceRunning = Utils.isServiceRunning(BluetoothSpeckService::class.java, applicationContext)
        Log.i("service", "isServiceRunning = $isServiceRunning")

        if (!isServiceRunning) {
            Log.i("service", "Starting BLT service")
            val simpleIntent = Intent(this, BluetoothSpeckService::class.java)
            this.startService(simpleIntent)
        }
        else {
            Log.i("service", "Service already running, restart")
            this.stopService(Intent(this, BluetoothSpeckService::class.java))
            Toast.makeText(this, "restarting service with new sensors", Toast.LENGTH_SHORT).show()
            this.startService(Intent(this, BluetoothSpeckService::class.java))
        }
    }
}



@Composable
fun DeviceIdTextField(defaultRespeckId: String, defaultThingyId: String) {
    Card (
        Modifier
            .fillMaxWidth()
            .padding(20.dp)
            .height(IntrinsicSize.Min),
        elevation = 10.dp,
    ) {
        Column (Modifier.height(IntrinsicSize.Min)) {
            val context = LocalContext.current
            val localFocusManager = LocalFocusManager.current
//            val scope = rememberCoroutineScope()

            var respeckId by remember { mutableStateOf(TextFieldValue(defaultRespeckId)) }
            OutlinedTextField(value = respeckId,
                modifier = Modifier
                    .padding(8.dp)
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                label = { Text(text = "Respeck ID") },
                placeholder = { Text(text = "Respeck ID") },
                onValueChange = {
                    respeckId = it
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = { localFocusManager.moveFocus(FocusDirection.Down) }
                )
            )

//            Text(text = respeckId.text,
//                modifier = Modifier.align(alignment = Alignment.CenterHorizontally))

            var thingyId by remember { mutableStateOf(TextFieldValue(defaultThingyId)) }
            OutlinedTextField(value = thingyId,
                modifier = Modifier
                    .padding(8.dp)
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                label = { Text(text = "Thingy ID") },
                placeholder = { Text(text = "Thingy ID") },
                onValueChange = {
                    thingyId = it
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = { localFocusManager.clearFocus() }
                )
            )

//            Text(text = thingyId.text,
//                modifier = Modifier.align(alignment = Alignment.CenterHorizontally))

//            val userNameKey = stringPreferencesKey("sound")
//            val userName = flow {
//                context.dataStore.data.map {
//                    it[userNameKey]
//                }.collect(collector = {
//                    if (it != null) {
//                        this.emit(it)
//                    }
//                })
//            }.collectAsState(initial = "")
//
//            OutlinedButton(
//                modifier = Modifier
//                    .padding(8.dp)
//                    .fillMaxWidth()
//                    .height(IntrinsicSize.Min),
//                onClick = {
//                    scope.launch {
//                        setSoundAsTrue(context, respeckId.text)
//                    }
//                }
//            ) {
//                Text("Save")
//            }
//
//            OutlinedButton(
//                modifier = Modifier
//                    .padding(8.dp)
//                    .fillMaxWidth()
//                    .height(IntrinsicSize.Min),
//                onClick = {
//                    Toast.makeText(context, userName.value, Toast.LENGTH_SHORT).show()
//                }
//            ) {
//                Text("Read")
//            }

            OutlinedButton(
                modifier = Modifier
                    .padding(8.dp)
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                onClick = {
                    context.startActivity(Intent(context, ConnectingActivity::class.java))
                }
            ) {
                Text("Pair")
            }

            DeviceLiveDataView()
        }
    }
}

@Composable
fun DeviceLiveDataView() {
    Column {
        val context = LocalContext.current

        val respeckAccX = flow {
            context.dataStore.data.map {
                it[stringPreferencesKey("respeckAccX")]
            }.collect(collector = {
                if (it != null) {
                    this.emit(it)
                }
            })
        }.collectAsState(initial = "0")
        val respeckAccY = flow {
            context.dataStore.data.map {
                it[stringPreferencesKey("respeckAccY")]
            }.collect(collector = {
                if (it != null) {
                    this.emit(it)
                }
            })
        }.collectAsState(initial = "0")
        val respeckAccZ = flow {
            context.dataStore.data.map {
                it[stringPreferencesKey("respeckAccZ")]
            }.collect(collector = {
                if (it != null) {
                    this.emit(it)
                }
            })
        }.collectAsState(initial = "0")

        val respeckGyrX = flow {
            context.dataStore.data.map {
                it[stringPreferencesKey("respeckGyrX")]
            }.collect(collector = {
                if (it != null) {
                    this.emit(it)
                }
            })
        }.collectAsState(initial = "0")
        val respeckGyrY = flow {
            context.dataStore.data.map {
                it[stringPreferencesKey("respeckGyrY")]
            }.collect(collector = {
                if (it != null) {
                    this.emit(it)
                }
            })
        }.collectAsState(initial = "0")
        val respeckGyrZ = flow {
            context.dataStore.data.map {
                it[stringPreferencesKey("respeckGyrZ")]
            }.collect(collector = {
                if (it != null) {
                    this.emit(it)
                }
            })
        }.collectAsState(initial = "0")

        val thingyAccX = flow {
            context.dataStore.data.map {
                it[stringPreferencesKey("thingyAccX")]
            }.collect(collector = {
                if (it != null) {
                    this.emit(it)
                }
            })
        }.collectAsState(initial = "0")
        val thingyAccY = flow {
            context.dataStore.data.map {
                it[stringPreferencesKey("thingyAccY")]
            }.collect(collector = {
                if (it != null) {
                    this.emit(it)
                }
            })
        }.collectAsState(initial = "0")
        val thingyAccZ = flow {
            context.dataStore.data.map {
                it[stringPreferencesKey("thingyAccZ")]
            }.collect(collector = {
                if (it != null) {
                    this.emit(it)
                }
            })
        }.collectAsState(initial = "0")

        val thingyGyrX = flow {
            context.dataStore.data.map {
                it[stringPreferencesKey("thingyGyrX")]
            }.collect(collector = {
                if (it != null) {
                    this.emit(it)
                }
            })
        }.collectAsState(initial = "0")
        val thingyGyrY = flow {
            context.dataStore.data.map {
                it[stringPreferencesKey("thingyGyrY")]
            }.collect(collector = {
                if (it != null) {
                    this.emit(it)
                }
            })
        }.collectAsState(initial = "0")
        val thingyGyrZ = flow {
            context.dataStore.data.map {
                it[stringPreferencesKey("thingyGyrZ")]
            }.collect(collector = {
                if (it != null) {
                    this.emit(it)
                }
            })
        }.collectAsState(initial = "0")

        val thingyMagX = flow {
            context.dataStore.data.map {
                it[stringPreferencesKey("thingyMagX")]
            }.collect(collector = {
                if (it != null) {
                    this.emit(it)
                }
            })
        }.collectAsState(initial = "0")
        val thingyMagY = flow {
            context.dataStore.data.map {
                it[stringPreferencesKey("thingyMagY")]
            }.collect(collector = {
                if (it != null) {
                    this.emit(it)
                }
            })
        }.collectAsState(initial = "0")
        val thingyMagZ = flow {
            context.dataStore.data.map {
                it[stringPreferencesKey("thingyMagZ")]
            }.collect(collector = {
                if (it != null) {
                    this.emit(it)
                }
            })
        }.collectAsState(initial = "0")
        
        Text(text = "Respeck")
        Text(text = "[${respeckAccX.value}, ${respeckAccY.value}, ${respeckAccZ.value}]")
        Text(text = "[${respeckGyrX.value}, ${respeckGyrY.value}, ${respeckGyrZ.value}]")
        Text(text = "Thingy")
        Text(text = "[${thingyAccX.value}, ${thingyAccY.value}, ${thingyAccZ.value}]")
        Text(text = "[${thingyGyrX.value}, ${thingyGyrY.value}, ${thingyGyrZ.value}]")
        Text(text = "[${thingyMagX.value}, ${thingyMagY.value}, ${thingyMagZ.value}]")
    }
}

suspend fun setSoundAsTrue(context: Context, value: String) {
    val userNameKey = stringPreferencesKey("sound")
    context.dataStore.edit {
        it[userNameKey] = value
    }
}

suspend fun setRespeckAcc(context: Context, accX: String, accY: String, accZ: String) {
    val respeckAccXKey = stringPreferencesKey("respeckAccX")
    val respeckAccYKey = stringPreferencesKey("respeckAccY")
    val respeckAccZKey = stringPreferencesKey("respeckAccZ")
    context.dataStore.edit {
        it[respeckAccXKey] = accX
        it[respeckAccYKey] = accY
        it[respeckAccZKey] = accZ
    }
}

suspend fun setRespeckGyr(context: Context, gyrX: String, gyrY: String, gyrZ: String) {
    val respeckGyrXKey = stringPreferencesKey("respeckGyrX")
    val respeckGyrYKey = stringPreferencesKey("respeckGyrY")
    val respeckGyrZKey = stringPreferencesKey("respeckGyrZ")
    context.dataStore.edit {
        it[respeckGyrXKey] = gyrX
        it[respeckGyrYKey] = gyrY
        it[respeckGyrZKey] = gyrZ
    }
}

suspend fun setThingyAcc(context: Context, accX: String, accY: String, accZ: String) {
    val thingyAccXKey = stringPreferencesKey("thingyAccX")
    val thingyAccYKey = stringPreferencesKey("thingyAccY")
    val thingyAccZKey = stringPreferencesKey("thingyAccZ")
    context.dataStore.edit {
        it[thingyAccXKey] = accX
        it[thingyAccYKey] = accY
        it[thingyAccZKey] = accZ
    }
}

suspend fun setThingyGyr(context: Context, gyrX: String, gyrY: String, gyrZ: String) {
    val thingyGyrXKey = stringPreferencesKey("thingyGyrX")
    val thingyGyrYKey = stringPreferencesKey("thingyGyrY")
    val thingyGyrZKey = stringPreferencesKey("thingyGyrZ")
    context.dataStore.edit {
        it[thingyGyrXKey] = gyrX
        it[thingyGyrYKey] = gyrY
        it[thingyGyrZKey] = gyrZ
    }
}

suspend fun setThingyMag(context: Context, magX: String, magY: String, magZ: String) {
    val thingyMagXKey = stringPreferencesKey("thingyMagX")
    val thingyMagYKey = stringPreferencesKey("thingyMagY")
    val thingyMagZKey = stringPreferencesKey("thingyMagZ")
    context.dataStore.edit {
        it[thingyMagXKey] = magX
        it[thingyMagYKey] = magY
        it[thingyMagZKey] = magZ
    }
}

@InternalTextApi
@Preview
@Composable
fun DefaultPreview() {
    DeviceIdTextField("", "")
}