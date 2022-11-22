package pdiot.v.polarbear

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.*
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.appcompat.content.res.AppCompatResources
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.ExperimentalLifecycleComposeApi
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.room.Room
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import com.simform.ssjetpackcomposeprogressbuttonlibrary.SSButtonState
import com.simform.ssjetpackcomposeprogressbuttonlibrary.SSButtonType
import com.simform.ssjetpackcomposeprogressbuttonlibrary.SSJetPackComposeProgressButton
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.tensorflow.lite.DataType
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import pdiot.v.polarbear.bluetooth.BluetoothSpeckService
import pdiot.v.polarbear.ml.AllModel90
import pdiot.v.polarbear.ml.Essential4Model99
import pdiot.v.polarbear.ui.theme.PolarBearTheme
import pdiot.v.polarbear.utils.Constants
import pdiot.v.polarbear.utils.RESpeckLiveData
import pdiot.v.polarbear.utils.ThingyLiveData
import pdiot.v.polarbear.utils.Utils
import kotlin.math.roundToInt
import kotlin.system.exitProcess
import pdiot.v.polarbear.login.Login
import pdiot.v.polarbear.ml.AllModelThingy
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList

val Context.deviceDataStore: DataStore<Preferences> by preferencesDataStore(name = "deviceSettings")

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

    private var defaultRespeckId = "E7:6E:9C:24:55:9A"
    private var defaultThingyId = "DF:80:AA:B3:5A:F7"

    private var respeckLiveWindow = MutableList(50 * 6) { 0.toFloat() }
    private var respeckBasicLiveWindow = MutableList(50 * 6) { 0.toFloat() }

    var respeckFirstRecord = true

    var respeckLastPredFlag = 0
    var respeckLastPredId = 0
    var respeckLastPredName = ""
    var respeckLastPredStartTime: Long = 0
    var respeckLastPredEndTime: Long = 0

    private var thingyLiveWindow = MutableList(50 * 6) { 0.toFloat() }

    private var thingyIsRunning = false

    var respeckResultList : FloatArray = FloatArray(14)

    var thingyLastPredState = 0

    var respeckLastPredTime: Long = System.currentTimeMillis()
    var respeckLastPredTimeBasic: Long = System.currentTimeMillis()
    var thingyLastPredTime: Long = System.currentTimeMillis()

    val predInterval = 1000

    private val cardsViewModel by viewModels<CardsViewModel>()

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

        locationPermissionRequest.launch(arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION))

        val actDb = Room.databaseBuilder(
            this,
            ActHistoryDb::class.java, "ActionHistory"
        ).build()

        initDeviceStates()

        resetDeviceStates()

        startSpeckService()

        Log.d(activityTAG, "onCreate: registering respeck receiver")
        // register respeck receiver
        respeckReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == Constants.ACTION_RESPECK_LIVE_BROADCAST) {
                    respeckLiveData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getSerializableExtra(Constants.RESPECK_LIVE_DATA, RESpeckLiveData::class.java)!!
                    } else {
                        intent.getSerializableExtra(Constants.RESPECK_LIVE_DATA) as RESpeckLiveData
                    }

                    Log.d("Live", "onReceive: liveData = $respeckLiveData")
                    lifecycleScope.launch {
                        setRespeckOn(context)
                        setRespeckLoading(context, false)
                        setRespeckAcc(context,
                            "${respeckLiveData.accelX}",
                            "${respeckLiveData.accelY}",
                            "${respeckLiveData.accelZ}")
                        setRespeckGyr(context,
                            "${respeckLiveData.gyro.x}",
                            "${respeckLiveData.gyro.y}",
                            "${respeckLiveData.gyro.z}")
                    }

                    val modelBasic = Essential4Model99.newInstance(context)

                    // Creates inputs for reference.
                    val inputFeature0Basic = TensorBuffer.createFixedSize(intArrayOf(1, 50, 6), DataType.FLOAT32)

                    Log.d("Basic Live Window Before Drop", "${respeckBasicLiveWindow}, ${respeckBasicLiveWindow.size}")

                    respeckBasicLiveWindow = respeckBasicLiveWindow.drop(6).toMutableList()

                    val inputArrayListBasic = respeckBasicLiveWindow
                    Log.d("Basic Live Window", "${inputArrayListBasic}, ${inputArrayListBasic.size}")
                    inputArrayListBasic.addAll(
                        arrayListOf(respeckLiveData.accelX, respeckLiveData.accelY, respeckLiveData.accelZ,
                            respeckLiveData.gyro.x, respeckLiveData.gyro.y, respeckLiveData.gyro.z)
                    )
                    Log.d("Basic Input Array", "${inputArrayListBasic}, ${inputArrayListBasic.size}")

                    val inputArrayBasic = inputArrayListBasic.toFloatArray()

                    inputFeature0Basic.loadArray(inputArrayBasic)

                    // Runs model inference and gets result.
                    val outputsBasic = modelBasic.process(inputFeature0Basic)
                    val outputFeature0Basic = outputsBasic.outputFeature0AsTensorBuffer

                    val floatArrayBasic = outputFeature0Basic.floatArray

                    val resultListBasic = getBasicResultList(outputFeature0Basic.floatArray)

                    Log.d("Basic Model Prediction", "${floatArrayBasic[0]}, ${floatArrayBasic[1]}, ${floatArrayBasic[2]}, ${floatArrayBasic[3]}")

                    // Releases model resources if no longer used.
                    modelBasic.close()

                    val actBasicMap = mapOf(
                        0 to "Sitting / Standing",
                        1 to "Lying Down",
                        2 to "Walking",
                        3 to "Running"
                    )

                    Log.d("Start With State", "$respeckLastPredId, $respeckLastPredFlag, $respeckLastPredStartTime")

                    if (respeckLastPredId == 0) {
                        val sharedPreferences = getSharedPreferences(Constants.PREFERENCES_FILE, Context.MODE_PRIVATE)

                        val lastId = sharedPreferences.getInt("lastPredictId", 0)

                        if (lastId == 0) {
                            respeckLastPredId += 1
                            sharedPreferences.edit().putInt(
                                "lastPredictId",
                                respeckLastPredId
                            ).apply()
                        } else {
                            respeckLastPredId = lastId + 1
                        }

                        if (respeckLastPredStartTime.toInt() == 0) {
                            respeckLastPredStartTime = System.currentTimeMillis()
                        }

                        Log.d("Basic State Update", "$respeckLastPredId, $respeckLastPredFlag, $respeckLastPredStartTime")
                    } else {
                        val respeckThisPredFlag = resultListBasic[0].first
                        if (respeckThisPredFlag != respeckLastPredFlag) {
                            val respeckThisPredStartTime = System.currentTimeMillis()
//                            val respeckThisPredInterval = respeckThisPredStartTime - respeckLastPredStartTime

                            respeckLastPredId += 1
                            respeckLastPredFlag = respeckThisPredFlag
                            respeckLastPredStartTime = respeckThisPredStartTime

//                            val entityAct = ActHistoryItem(
//                                actId = respeckLastPredId,
//                                actFlag = respeckThisPredFlag,
//                                actName = actBasicMap[respeckThisPredFlag]!!,
//                                actStartTime = respeckLastPredStartTime,
//                                actEndTime =  respeckLastPredEndTime,
//                                actInterval = respeckThisPredInterval
//                            )
//
//                            Log.d("Basic State Update", "$respeckLastPredId, $respeckLastPredFlag, $respeckLastPredStartTime")
//
//                            if (respeckThisPredInterval > 1000) {
//                                val historyDaoR = actDb.getHistoryDao()
//                                Log.d("Basic Exist DB", historyDaoR.getAll().toString())
//                                historyDaoR.insert(entityAct)
//
//                                val sharedPreferences = getSharedPreferences(Constants.PREFERENCES_FILE, Context.MODE_PRIVATE)
//                                sharedPreferences.edit().putInt(
//                                    "lastPredictId",
//                                    respeckLastPredId
//                                ).apply()
//                            }
                        } else {
                            val respeckThisPredStartTime = System.currentTimeMillis()
                            val respeckThisPredInterval = respeckThisPredStartTime - respeckLastPredStartTime

                            val entityAct = ActHistoryItem(
                                actId = respeckLastPredId,
                                actFlag = respeckThisPredFlag,
                                actName = actBasicMap[respeckThisPredFlag]!!,
                                actStartTime = respeckLastPredStartTime,
                                actEndTime =  respeckThisPredStartTime,
                                actInterval = respeckThisPredInterval
                            )

                            Log.d("Basic State Update", "$respeckLastPredId, $respeckLastPredFlag, $respeckLastPredStartTime")

                            if (respeckThisPredInterval > 1000) {
                                val historyDaoR = actDb.getHistoryDao()
                                Log.d("Basic Exist DB", historyDaoR.getAll().toString())
                                historyDaoR.insert(entityAct)

                                val sharedPreferences = getSharedPreferences(Constants.PREFERENCES_FILE, Context.MODE_PRIVATE)
                                sharedPreferences.edit().putInt(
                                    "lastPredictId",
                                    respeckLastPredId
                                ).apply()
                            }
                        }
                    }

                    if (System.currentTimeMillis() - respeckLastPredTimeBasic > predInterval) {
                        lifecycleScope.launch {
                            setPredBasic(context, resultListBasic)
                            respeckLastPredTimeBasic = System.currentTimeMillis()
                            Log.d("Last Prediction Time", "$respeckLastPredTimeBasic")

                            respeckLastPredFlag = resultListBasic[0].first
                            Log.d("Last Prediction State", "$respeckLastPredFlag")
                        }
                    }


                    val model = AllModel90.newInstance(context)

                    // Creates inputs for reference.
                    val inputFeature0 = TensorBuffer.createFixedSize(intArrayOf(1, 50, 6), DataType.FLOAT32)

                    Log.d("Live Window Before Drop", "${respeckLiveWindow}, ${respeckLiveWindow.size}")

                    respeckLiveWindow = respeckLiveWindow.drop(6).toMutableList()

                    val inputArrayList = respeckLiveWindow
                    Log.d("Live Window", "${inputArrayList}, ${inputArrayList.size}")
                    inputArrayList.addAll(
                        arrayListOf(respeckLiveData.accelX, respeckLiveData.accelY, respeckLiveData.accelZ,
                            respeckLiveData.gyro.x, respeckLiveData.gyro.y, respeckLiveData.gyro.z)
                    )
                    Log.d("Input Array", "${inputArrayList}, ${inputArrayList.size}")
                    val inputArray = inputArrayList.toFloatArray()

                    inputFeature0.loadArray(inputArray)

                    // Runs model inference and gets result.
                    val outputs = model.process(inputFeature0)
                    val outputFeature0 = outputs.outputFeature0AsTensorBuffer

                    val resultList = getResultList(outputFeature0.floatArray)
                    respeckResultList = outputFeature0.floatArray

                    Log.d("Model Prediction", resultList.toString())

                    // Releases model resources if no longer used.
                    model.close()

                    if (!thingyIsRunning) {
                        if (System.currentTimeMillis() - respeckLastPredTime > predInterval) {
                            lifecycleScope.launch {
                                setPred(context, resultList)
                                respeckLastPredTime = System.currentTimeMillis()
                                Log.d("Last Prediction Time", "$respeckLastPredTime")
                            }
                        }
                    }
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
                    thingyLiveData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getSerializableExtra(Constants.THINGY_LIVE_DATA, ThingyLiveData::class.java)!!
                    } else {
                        intent.getSerializableExtra(Constants.THINGY_LIVE_DATA) as ThingyLiveData
                    }

                    thingyIsRunning = true

                    Log.d("Live", "onReceive: thingyLiveData = $thingyLiveData")

                    val modelT = AllModelThingy.newInstance(context)
                    // Creates inputs for reference.

                    val inputFeatureT = TensorBuffer.createFixedSize(intArrayOf(1, 50, 6), DataType.FLOAT32)

                    Log.d("Live Window Before Drop", "${thingyLiveWindow}, ${thingyLiveWindow.size}")
                    thingyLiveWindow = thingyLiveWindow.drop(6).toMutableList()

                    Log.d("Live Window", "${thingyLiveWindow}, ${thingyLiveWindow.size}")
                    val inputArrayT = thingyLiveWindow
                    inputArrayT.addAll(
                        arrayListOf(thingyLiveData.accelX, thingyLiveData.accelY, thingyLiveData.accelZ,
                            thingyLiveData.gyro.x, thingyLiveData.gyro.y, thingyLiveData.gyro.z)
                    )
                    inputFeatureT.loadArray(inputArrayT.toFloatArray())
                    // Runs model inference and gets result.

                    val outputsT = modelT.process(inputFeatureT)
                    val outputFeatureT = outputsT.outputFeature0AsTensorBuffer
//                    val resultListT = getResultList(outputFeatureT.floatArray)

                    val finalResultListT = getResultList(comparePrediction(outputFeatureT.floatArray, respeckResultList))

                    lifecycleScope.launch {
                        setThingyOn(context)
                        setThingyLoading(context, false)
                        setThingyAcc(context,
                            "${thingyLiveData.accelX}",
                            "${thingyLiveData.accelY}",
                            "${thingyLiveData.accelZ}")
                        setThingyGyr(context,
                            "${thingyLiveData.gyro.x}",
                            "${thingyLiveData.gyro.y}",
                            "${thingyLiveData.gyro.z}")
                        setThingyMag(context,
                            "${thingyLiveData.mag.x}",
                            "${thingyLiveData.mag.y}",
                            "${thingyLiveData.mag.z}")
                        if (System.currentTimeMillis() - thingyLastPredTime > predInterval) {
                            setPred(context, finalResultListT)
                            thingyLastPredTime = System.currentTimeMillis()
                            Log.d("Last Prediction Time", "$thingyLastPredTime")
                        }
                    }
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
//                    DeviceIdTextField(defaultRespeckId, defaultThingyId)
                    AppScreen(cardsViewModel, actDb)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        unregisterReceiver(respeckReceiver)
        unregisterReceiver(thingyReceiver)
        respeckLooper.quit()
        thingyLooper.quit()

        resetDeviceStates()

        exitProcess(0)
    }

    private fun startSpeckService() {
        val sharedPreferences = getSharedPreferences(Constants.PREFERENCES_FILE, Context.MODE_PRIVATE)

        sharedPreferences.edit().putString(
            Constants.RESPECK_MAC_ADDRESS_PREF,
            defaultRespeckId
        ).apply()
        sharedPreferences.edit().putInt(Constants.RESPECK_VERSION, 6).apply()

        sharedPreferences.edit().putString(
            Constants.THINGY_MAC_ADDRESS_PREF,
            defaultThingyId
        ).apply()

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

    private fun initDeviceStates() {
        lifecycleScope.launch {
            setRespeckLoading(this@MainActivity, true)
            setThingyLoading(this@MainActivity, true)
        }
    }

    private fun resetDeviceStates() {
        lifecycleScope.launch {
            setRespeckOff(this@MainActivity)
            setThingyOff(this@MainActivity)
        }
    }

    fun getBasicResultList(arr: FloatArray): List<Pair<Int, Float>> {
        val resultMap = mutableMapOf<Int, Float>()

        for (i in 0..3) {
            resultMap[i] = arr[i]
        }

        return resultMap.toList().sortedBy { (key, value) -> value }.reversed()
    }

    fun getResultList(arr: FloatArray): List<Pair<Int, Float>> {
        val resultMap = mutableMapOf<Int, Float>()

        for (i in 0..13) {
            resultMap[i] = arr[i]
        }

        return resultMap.toList().sortedBy { (key, value) -> value }.reversed()
    }

    private fun comparePrediction(aR:FloatArray, aT:FloatArray) : FloatArray{
        val resultList : MutableList<Float> = mutableListOf()
        if (aR.isNotEmpty() && aT.isNotEmpty()){
            for (i in 0 .. aR.size-1){
                resultList.add((aR[i]+aT[i])/2)
            }
        }
        return resultList.toFloatArray()

    }

    @Composable
    fun stateModelType(): State<Int> {
        val context = LocalContext.current

        val modelType = flow {
            context.deviceDataStore.data.map {
                it[intPreferencesKey("modelType")]
            }.collect(collector = {
                if (it != null) {
                    this.emit(it)
                }
            })
        }.collectAsState(initial = 1)

        return remember { modelType }
    }

    @Composable
    fun AppScreen(viewModel: CardsViewModel, actDb: ActHistoryDb) {
        val navController = rememberNavController ()
        Scaffold (
            topBar = {  },
            floatingActionButtonPosition = FabPosition.End,
            floatingActionButton = { FloatingPairingButton() },
            bottomBar = { BottomNavigation (navController = navController) }
        ) { paddingValues ->
            NavigationGraph (navController = navController, innerPadding = paddingValues, viewModel, actDb)
        }
    }

    @Composable
    fun NavigationGraph(navController: NavHostController, innerPadding: PaddingValues, viewModel: CardsViewModel, actDb: ActHistoryDb) {
        NavHost(navController, startDestination = BottomNavItem.Home.screenRoute) {
            composable(BottomNavItem.Home.screenRoute) {
                HomeScreen(innerPadding, viewModel, actDb)
            }
            composable(BottomNavItem.Device.screenRoute) {
                DeviceScreen(innerPadding)
            }
            composable(BottomNavItem.Account.screenRoute) {
                AccountScreen(innerPadding)
            }
        }
    }

    @Composable
    fun BottomNavigation(navController: NavController) {
        val items = listOf(
            BottomNavItem.Home,
            BottomNavItem.Device,
            BottomNavItem.Account,
        )
        BottomNavigation(
            backgroundColor = Color.White,
            contentColor = Color.Black
        ) {
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentRoute = navBackStackEntry?.destination?.route
            items.forEach { item ->
                BottomNavigationItem(
                    icon = { Icon(painterResource(id = item.icon), contentDescription = item.screenTitle) },
                    label = { Text(text = item.screenTitle, fontSize = 9.sp) },
                    selectedContentColor = Color.Blue.copy(0.7f),
                    unselectedContentColor = Color.Black.copy(0.7f),
                    alwaysShowLabel = true,
                    selected = currentRoute == item.screenRoute,
                    onClick = {
                        navController.navigate(item.screenRoute) {
                            navController.graph.startDestinationRoute?.let { screen_route ->
                                popUpTo(screen_route) {
                                    saveState = true
                                }
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        }
    }

    @Composable
    fun FloatingPairingButton() {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()

        val respeckOn = flow {
            context.deviceDataStore.data.map {
                it[booleanPreferencesKey("respeckOn")]
            }.collect {
                if (it != null) {
                    this.emit(it)
                }
            }
        }.collectAsState(initial = false).value

        val respeckId = flow {
            context.deviceDataStore.data.map {
                it[stringPreferencesKey("respeckId")]
            }.collect {
                if (it != null) {
                    this.emit(it)
                }
            }
        }.collectAsState(initial = "").value

        val thingyOn = flow {
            context.deviceDataStore.data.map {
                it[booleanPreferencesKey("thingyOn")]
            }.collect {
                if (it != null) {
                    this.emit(it)
                }
            }
        }.collectAsState(initial = false).value

        val thingyId = flow {
            context.deviceDataStore.data.map {
                it[stringPreferencesKey("thingyId")]
            }.collect {
                if (it != null) {
                    this.emit(it)
                }
            }
        }.collectAsState(initial = "").value

        FloatingActionButton(
            backgroundColor = Color.White,
            contentColor = if (thingyOn || respeckOn) {Color.Blue.copy(0.7f)} else {Color.Gray},
            onClick = {
                scope.launch {
                    if (!thingyOn && !respeckOn) {
                        syncAllPairing(context, respeckId, thingyId)
                    }

                    if (!respeckOn) {
                        startRespeckPairing(context, respeckId)
                    }

                    if (!thingyOn) {
                        startThingyPairing(context, thingyId)
                    }
                }
            }
        ){
            Icon(if (respeckOn && thingyOn) {
                painterResource(id = R.drawable.bluetooth_connected)
            } else {
                if (respeckOn || thingyOn) {
                    painterResource(id = R.drawable.sync)
                } else {
                    painterResource(id = R.drawable.bluetooth_connected)
                }
            }, contentDescription = null)
        }
    }


    sealed class BottomNavItem(var screenTitle: String, var icon: Int, var screenRoute: String){
        object Home : BottomNavItem("Home", R.drawable.menu, "home_route")
        object Device: BottomNavItem("Device", R.drawable.pairing,"device_route")
        object Account: BottomNavItem("Account", R.drawable.person,"account_route")
    }


    @RequiresApi(Build.VERSION_CODES.O)
    @Composable
    fun HomeScreen(innerPadding: PaddingValues, viewModel: CardsViewModel, actDb: ActHistoryDb) {
//    val mainViewModel: CountViewModel = viewModel()
//    val seconds by mainViewModel.seconds.collectAsState(initial = "00")

        Column (
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val historyDao = actDb.getHistoryDao()

            ActPredictList()

            Row(
                modifier = Modifier
                    .padding(32.dp, 16.dp, 32.dp, 16.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.End) {
                Icon(painterResource(id = R.drawable.clearall), null,
                    modifier=Modifier.clickable {
                        Thread {
                            actDb.getHistoryDao().clear()
                        }.start()
                    },
                    tint = colorResource(id = R.color.teal),
                )
            }

            val terms = historyDao.getAllFlow().collectAsState(initial = listOf()).value.reversed()

            val actBasicInfoMap = mapOf(
                0 to ActionInfo(0, "Sitting / Standing", R.drawable.sitting40),
                1 to ActionInfo(5, "Lying Down", R.drawable.lying40),
                2 to ActionInfo(9, "Walking", R.drawable.walking40),
                3 to ActionInfo(10, "Running", R.drawable.running40)
            )

            LazyColumn {
                itemsIndexed(terms) { index, item ->
                    Card(
                        backgroundColor = if (index == 0) {Color(0xFFa478bb)} else {Color(0xFFf7e7ff)},
                        elevation = 4.dp,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                horizontal = 24.dp,
                                vertical = 8.dp
                            )
                            .height(72.dp)
                    ){
                        Row (modifier = Modifier.fillMaxSize()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .weight(2f),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(painterResource(id = actBasicInfoMap[item.actFlag]!!.icon), null,
                                tint = if (index == 0) {Color.White} else {Color.Black})
                            }

                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .weight(6f),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .weight(1f),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(text = item.actName, textAlign = TextAlign.Center, color = if (index == 0) {Color.White} else {Color.Black})
                                }

                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .weight(1f),
                                    contentAlignment = Alignment.Center
                                ) {
                                    val date = Date(item.actStartTime)
                                    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.UK)
                                    val startDt = sdf.format(date)
                                    Text(text = "From $startDt", textAlign = TextAlign.Center, color = if (index == 0) {Color.White} else {Color.Black})
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .weight(2f),
                                contentAlignment = Alignment.Center
                            ) {
                                val totalTime = item.actInterval / 1000
                                val minutes = (totalTime % 3600) / 60
                                val seconds = totalTime % 60

                                val intervalString = String.format("%02d:%02d", minutes, seconds)

                                Text(text = intervalString, textAlign = TextAlign.Center, color = if (index == 0) {Color.White} else {Color.Black})
                            }

                        }
                    }
                }
            }
//            ActHistoryList(viewModel, actDb)
        }



//        AnimatedContent(
//            targetState = seconds,
//            transitionSpec = {
//                addAnimation().using(
//                    SizeTransform(clip = false)
//                )
//            }
//        ) { targetCount ->
//            Text(
//                text = "$targetCount",
//                style = TextStyle(fontSize = MaterialTheme.typography.h1.fontSize),
//                textAlign = TextAlign.Center
//            )
//        }

//            Column (
//                modifier = Modifier.fillMaxSize(),
//                verticalArrangement = Arrangement.Top,
//                horizontalAlignment = Alignment.CenterHorizontally) {
//                CircularProgressbar3()
//            }

    }

    @OptIn(ExperimentalFoundationApi::class, ExperimentalAnimationApi::class)
    @Composable
    fun ActPredictList() {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()

        val modelType = flow<Int> {
            context.deviceDataStore.data.map {
                it[intPreferencesKey("modelType")]
            }.collect {
                if (it != null) {
                    this.emit(it)
                }
            }
        }.collectAsState(initial = 1).value

        Row(
            modifier = Modifier
                .padding(32.dp, 16.dp, 32.dp, 0.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.End) {
            Icon(painterResource(id = R.drawable.chart), null,
                modifier=Modifier.clickable {
                    scope.launch {
                        if (modelType == 0) {
                            setModelType(context, 1)
                        } else {
                            if (modelType == 1) {
                                setModelType(context, 0)
                            }
                        }
                    }
                },
                tint = if (modelType == 1) {Color.Blue.copy(0.7f)} else {colorResource(id = R.color.teal)},
            )
        }

        Card (
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)) {
            val actInfoMap = mapOf(
                0 to ActionInfo(11, "Climbing stairs", R.drawable.walking),
                1 to ActionInfo(12, "Descending stairs", R.drawable.walking),
                2 to ActionInfo(3, "Desk work", R.drawable.sitting),
                3 to ActionInfo(0, "Sitting", R.drawable.sitting),
                4 to ActionInfo(1, "Sitting Bent Backward", R.drawable.sitting),
                5 to ActionInfo(2, "Sitting Bent Forward", R.drawable.sitting),
                6 to ActionInfo(4, "Standing", R.drawable.standing),
                7 to ActionInfo(5, "Lying on left", R.drawable.lying),
                8 to ActionInfo(8, "Lying on back", R.drawable.lying),
                9 to ActionInfo(7, "Lying on stomach", R.drawable.lying),
                10 to ActionInfo(6, "Lying on right", R.drawable.lying),
                11 to ActionInfo(13, "Movement", R.drawable.movement),
                12 to ActionInfo(10, "Running", R.drawable.running),
                13 to ActionInfo(9, "Walking", R.drawable.walking),
            )

            val actBasicInfoMap = mapOf(
                0 to ActionInfo(0, "Sitting / Standing", R.drawable.sitting),
                1 to ActionInfo(5, "Lying Down", R.drawable.lying),
                2 to ActionInfo(9, "Walking", R.drawable.walking),
                3 to ActionInfo(10, "Running", R.drawable.running)
            )

            val pred0I = flow<Int> {
                context.deviceDataStore.data.map {
                    it[intPreferencesKey("pred0I")]
                }.collect {
                    if (it != null) {
                        this.emit(it)
                    }
                }
            }.collectAsState(initial = 0).value

            val pred0C = flow<Float> {
                context.deviceDataStore.data.map {
                    it[floatPreferencesKey("pred0C")]
                }.collect {
                    if (it != null) {
                        this.emit(it)
                    }
                }
            }.collectAsState(initial = 0.0).value.toFloat()

            val pred1I = flow<Int> {
                context.deviceDataStore.data.map {
                    it[intPreferencesKey("pred1I")]
                }.collect {
                    if (it != null) {
                        this.emit(it)
                    }
                }
            }.collectAsState(initial = 0).value

            val pred1C = flow<Float> {
                context.deviceDataStore.data.map {
                    it[floatPreferencesKey("pred1C")]
                }.collect {
                    if (it != null) {
                        this.emit(it)
                    }
                }
            }.collectAsState(initial = 0.0).value.toFloat()

            val pred2I = flow<Int> {
                context.deviceDataStore.data.map {
                    it[intPreferencesKey("pred2I")]
                }.collect {
                    if (it != null) {
                        this.emit(it)
                    }
                }
            }.collectAsState(initial = 0).value

            val pred2C = flow<Float> {
                context.deviceDataStore.data.map {
                    it[floatPreferencesKey("pred2C")]
                }.collect {
                    if (it != null) {
                        this.emit(it)
                    }
                }
            }.collectAsState(initial = 0.0).value.toFloat()

            val pred3I = flow<Int> {
                context.deviceDataStore.data.map {
                    it[intPreferencesKey("pred3I")]
                }.collect {
                    if (it != null) {
                        this.emit(it)
                    }
                }
            }.collectAsState(initial = 0).value

            val pred3C = flow<Float> {
                context.deviceDataStore.data.map {
                    it[floatPreferencesKey("pred3C")]
                }.collect {
                    if (it != null) {
                        this.emit(it)
                    }
                }
            }.collectAsState(initial = 0.0).value.toFloat()

            val actList by remember {
                mutableStateOf(listOf(
                    pred0I,
                    pred1I,
                    pred2I,
                    pred3I
                ))
            }

            val actMap = mapOf(
                0 to pred0I,
                1 to pred1I,
                2 to pred2I,
                3 to pred3I
            )

            val actCMap = mapOf(
                0 to pred0C,
                1 to pred1C,
                2 to pred2C,
                3 to pred3C
            )

            val predBasic0I = flow<Int> {
                context.deviceDataStore.data.map {
                    it[intPreferencesKey("predBasic0I")]
                }.collect {
                    if (it != null) {
                        this.emit(it)
                    }
                }
            }.collectAsState(initial = 0).value

            val predBasic0C = flow<Float> {
                context.deviceDataStore.data.map {
                    it[floatPreferencesKey("predBasic0C")]
                }.collect {
                    if (it != null) {
                        this.emit(it)
                    }
                }
            }.collectAsState(initial = 0.0).value.toFloat()

            val predBasic1I = flow<Int> {
                context.deviceDataStore.data.map {
                    it[intPreferencesKey("predBasic1I")]
                }.collect {
                    if (it != null) {
                        this.emit(it)
                    }
                }
            }.collectAsState(initial = 0).value

            val predBasic1C = flow<Float> {
                context.deviceDataStore.data.map {
                    it[floatPreferencesKey("predBasic1C")]
                }.collect {
                    if (it != null) {
                        this.emit(it)
                    }
                }
            }.collectAsState(initial = 0.0).value.toFloat()

            val predBasic2I = flow<Int> {
                context.deviceDataStore.data.map {
                    it[intPreferencesKey("predBasic2I")]
                }.collect {
                    if (it != null) {
                        this.emit(it)
                    }
                }
            }.collectAsState(initial = 0).value

            val predBasic2C = flow<Float> {
                context.deviceDataStore.data.map {
                    it[floatPreferencesKey("predBasic2C")]
                }.collect {
                    if (it != null) {
                        this.emit(it)
                    }
                }
            }.collectAsState(initial = 0.0).value.toFloat()

            val predBasic3I = flow<Int> {
                context.deviceDataStore.data.map {
                    it[intPreferencesKey("predBasic3I")]
                }.collect {
                    if (it != null) {
                        this.emit(it)
                    }
                }
            }.collectAsState(initial = 0).value

            val predBasic3C = flow<Float> {
                context.deviceDataStore.data.map {
                    it[floatPreferencesKey("predBasic3C")]
                }.collect {
                    if (it != null) {
                        this.emit(it)
                    }
                }
            }.collectAsState(initial = 0.0).value.toFloat()

            val actBasicList by remember {
                mutableStateOf(listOf(
                    predBasic0I,
                    predBasic1I,
                    predBasic2I,
                    predBasic3I
                ))
            }

            val actBasicMap = mapOf(
                0 to predBasic0I,
                1 to predBasic1I,
                2 to predBasic2I,
                3 to predBasic3I
            )

            val actCBasicMap = mapOf(
                0 to predBasic0C,
                1 to predBasic1C,
                2 to predBasic2C,
                3 to predBasic3C
            )

//            Text(text = "$pred0I, $pred1I, $pred2I, $pred3I")

            LazyColumn {
                itemsIndexed(if (modelType == 1) {actBasicList} else {actList}) { index, _ ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
//                            .clickable {
//                                actionList = actionList
//                                    .shuffled()
//                                    .slice(0..4)
//                            }
                            .animateItemPlacement()
                            .padding(16.dp, 16.dp, 16.dp, 16.dp)) {

                        // if (index == 0) {} else {if (index == 1) else { if (index == 2) else { if (index == 3) else { [pred3I] }}} })
                        Row (modifier = Modifier.weight(8f)) {
                            Icon(painterResource(
                                id = if (modelType == 1) {actBasicInfoMap[actBasicMap[index]]!!.icon}
                                else {actInfoMap[actMap[index]]!!.icon}),
                                null)
                            Column (modifier = Modifier.padding(16.dp, 0.dp, 16.dp, 0.dp)) {
//                                val actionCategory = if (item.id in listOf(0, 1, 2, 3)) { "sitting" }
//                                else { if (item.id == 4) { "standing" }
//                                else { if (item.id in listOf(5, 6, 7, 8)) { "lying" }
//                                else { if (item.id == 9) { "walking" }
//                                else { if (item.id == 10) { "running" }
//                                else { if (item.id in listOf(11, 12)) { "walking" }
//                                else { if (item.id == 13) { "movement" }
//                                else { "" } } } } } } }

                                Text(text = if (modelType == 1) {actBasicInfoMap[actBasicMap[index]]!!.name} else {actInfoMap[actMap[index]]!!.name})
                                Spacer(modifier = Modifier.height(2.dp))
                                LinearProgressIndicator(progress = if (modelType == 1) {actCBasicMap[index]!!} else {actCMap[index]!!},
                                modifier = Modifier.height(3.dp), color = Color.Blue.copy(0.7f))
                            }
                        }
                        Text(text = if (modelType == 1) {"${(actCBasicMap[index]!! * 100.0).roundToInt() / 100.0}"} else {"${(actCMap[index]!! * 100.0).roundToInt() / 100.0}"}, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalLifecycleComposeApi::class)
    @Composable
    fun ActHistoryList(viewModel: CardsViewModel, actDb: ActHistoryDb) {
        val cards by viewModel.cards.collectAsStateWithLifecycle()
        val expandedCardIds by viewModel.expandedCardIdsList.collectAsStateWithLifecycle()

        LazyColumn {
            items(cards, ExpandableCardModel::id) { card ->
                ExpandableCard(
                    card = card,
                    onCardArrowClick = { viewModel.onCardArrowClicked(card.id) },
                    expanded = expandedCardIds.contains(card.id),
                )
            }
        }
    }

    @SuppressLint("UnusedTransitionTargetStateParameter")
    @Composable
    fun ExpandableCard(
        card: ExpandableCardModel,
        onCardArrowClick: () -> Unit,
        expanded: Boolean,
    ) {
        val cardCollapsedBackgroundColor = Color(0xFFFEFFFD)
        val cardExpandedBackgroundColor = Color(0xFFFFDA6D)
        val transitionState = remember {
            MutableTransitionState(expanded).apply {
                targetState = !expanded
            }
        }
        val transition = updateTransition(transitionState, label = "transition")
        val cardBgColor by transition.animateColor({
            tween(durationMillis = EXPANSTION_TRANSITION_DURATION)
        }, label = "bgColorTransition") {
            if (expanded) cardExpandedBackgroundColor else cardCollapsedBackgroundColor
        }
        val cardPaddingHorizontal by transition.animateDp({
            tween(durationMillis = EXPANSTION_TRANSITION_DURATION)
        }, label = "paddingTransition") {
            if (expanded) 48.dp else 24.dp
        }
        val cardElevation by transition.animateDp({
            tween(durationMillis = EXPANSTION_TRANSITION_DURATION)
        }, label = "elevationTransition") {
            if (expanded) 24.dp else 4.dp
        }
        val cardRoundedCorners by transition.animateDp({
            tween(
                durationMillis = EXPANSTION_TRANSITION_DURATION,
                easing = FastOutSlowInEasing
            )
        }, label = "cornersTransition") {
            if (expanded) 0.dp else 16.dp
        }
        val arrowRotationDegree by transition.animateFloat({
            tween(durationMillis = EXPANSTION_TRANSITION_DURATION)
        }, label = "rotationDegreeTransition") {
            if (expanded) 0f else 180f
        }
        val context = LocalContext.current
        val contentColour = remember {
            Color(ContextCompat.getColor(context, R.color.colorDayNightPurple))
        }

        Card(
            backgroundColor = cardBgColor,
            contentColor = contentColour,
            elevation = cardElevation,
            shape = RoundedCornerShape(cardRoundedCorners),
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = cardPaddingHorizontal,
                    vertical = 8.dp
                )
        ) {
            Column {
                Box {
                    CardArrow(
                        degrees = arrowRotationDegree,
                        onClick = onCardArrowClick
                    )
                    CardTitle(title = card.title)
                }
                ExpandableContent(visible = expanded)
            }
        }
    }

    @Composable
    fun CardArrow(
        degrees: Float,
        onClick: () -> Unit
    ) {
        IconButton(
            onClick = onClick,
            content = {
                Icon(
                    painter = painterResource(id = R.drawable.ic_expand_less_24),
                    contentDescription = "Expandable Arrow",
                    modifier = Modifier.rotate(degrees),
                )
            }
        )
    }

    @Composable
    fun CardTitle(title: String) {
        Text(
            text = title,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            textAlign = TextAlign.Center,
        )
    }

    @Composable
    fun ExpandableContent(
        visible: Boolean = true,
    ) {
        val enterTransition = remember {
            expandVertically(
                expandFrom = Alignment.Top,
                animationSpec = tween(Companion.EXPANSTION_TRANSITION_DURATION)
            ) + fadeIn(
                initialAlpha = 0.3f,
                animationSpec = tween(Companion.EXPANSTION_TRANSITION_DURATION)
            )
        }
        val exitTransition = remember {
            shrinkVertically(
                // Expand from the top.
                shrinkTowards = Alignment.Top,
                animationSpec = tween(Companion.EXPANSTION_TRANSITION_DURATION)
            ) + fadeOut(
                // Fade in with the initial alpha of 0.3f.
                animationSpec = tween(Companion.EXPANSTION_TRANSITION_DURATION)
            )
        }

        AnimatedVisibility(
            visible = visible,
            enter = enterTransition,
            exit = exitTransition
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                Spacer(modifier = Modifier.heightIn(100.dp))
                Text(
                    text = "Expandable content here",
                    textAlign = TextAlign.Center
                )
            }
        }
    }

    @Composable
    fun CircularProgressbar3(
        number: Float = 70f,
        numberStyle: TextStyle = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Bold,
            fontSize = 28.sp
        ),
        size: Dp = 180.dp,
        innerSize: Dp = 120.dp,
        indicatorThickness: Dp = 28.dp,
        animationDuration: Int = 1000,
        animationDelay: Int = 0,
        foregroundIndicatorColor: Color = Color(0xFF35898f),
        innerForegroundIndicatorColor: Color = Color.Blue.copy(0.8f),
        backgroundIndicatorColor: Color = Color.LightGray.copy(alpha = 0.3f),
    ) {

        // It remembers the number value
        var numberR by remember {
            mutableStateOf(0f)
        }

        // It remembers the number value
        var innerNumberR by remember {
            mutableStateOf(0f)
        }

        // Number Animation
        val animateNumber = animateFloatAsState(
            targetValue = numberR,
            animationSpec = tween(
                durationMillis = animationDuration,
                delayMillis = animationDelay
            )
        )

        // Number Animation
        val innerAnimateNumber = animateFloatAsState(
            targetValue = innerNumberR,
            animationSpec = tween(
                durationMillis = animationDuration,
                delayMillis = animationDelay
            )
        )

        // This is to start the animation when the activity is opened
        LaunchedEffect(Unit) {
            numberR = number
            innerNumberR = number
        }

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(size = size)
        ) {
            Canvas(
                modifier = Modifier
                    .size(size = size)
            ) {
                // Background circle
                drawCircle(
                    color = backgroundIndicatorColor,
                    radius = size.toPx() / 2,
                    style = Stroke(width = indicatorThickness.toPx(), cap = StrokeCap.Round)
                )

                val sweepAngle = (animateNumber.value / 100) * 360

                // Foreground circle
                drawArc(
                    color = foregroundIndicatorColor,
                    startAngle = -90f,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    style = Stroke(indicatorThickness.toPx(), cap = StrokeCap.Round)
                )
            }

            // Text that shows number inside the circle
            Text(
                text = (animateNumber.value).toInt().toString(),
                style = numberStyle
            )

            Canvas(
                modifier = Modifier
                    .size(size = innerSize)
            ) {
                // Background circle
                drawCircle(
                    color = backgroundIndicatorColor,
                    radius = innerSize.toPx() / 2,
                    style = Stroke(width = indicatorThickness.toPx(), cap = StrokeCap.Round)
                )

                val sweepAngle = (innerAnimateNumber.value / 100) * 360

                // Foreground circle
                drawArc(
                    color = innerForegroundIndicatorColor,
                    startAngle = -90f,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    style = Stroke(indicatorThickness.toPx(), cap = StrokeCap.Round)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        ButtonProgressbar {
            numberR = (1..100).random().toFloat()
            innerNumberR = (1..100).random().toFloat()
        }
    }

    @Composable
    private fun ButtonProgressbar(
        backgroundColor: Color = Color(0xFF35898f),
        onClickButton: () -> Unit,
    ) {
        Button(
            onClick = {
                onClickButton()
            },
            colors = ButtonDefaults.buttonColors(
                backgroundColor = backgroundColor
            )
        ) {
            Text(
                text = "Random",
                color = Color.White,
                fontSize = 16.sp
            )
        }
    }


    @ExperimentalAnimationApi
    fun addAnimation(duration: Int = 1000): ContentTransform {
//    return slideInVertically(animationSpec = tween(durationMillis = duration)) { height -> height } + fadeIn(
//        animationSpec = tween(durationMillis = duration)
//    ) with slideOutVertically(animationSpec = tween(durationMillis = duration)) { height -> -height } + fadeOut(
//        animationSpec = tween(durationMillis = duration)
//    )

        return expandVertically(animationSpec = tween(durationMillis = duration)) { height -> height } + fadeIn(
            animationSpec = tween(durationMillis = duration)
        ) with shrinkVertically(animationSpec = tween(durationMillis = duration)) { height -> -height } + fadeOut(
            animationSpec = tween(durationMillis = duration)
        )
    }

    class CountViewModel : ViewModel() {
        val seconds = (0..100)
            .asSequence()
            .asFlow()
            .map {
                if (it in 0..9) "0$it" else it
            }
            .onEach { delay(1000) }
    }



    @Composable
    fun DeviceScreen(innerPadding: PaddingValues) {
        Column (
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.width(32.dp))

            ConstraintLayout (
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .systemBarsPadding()
            ) {
                val (respeckCard, thingyCard) = createRefs()
                val cardEvaluation = 5.dp

                Card (
                    modifier = Modifier
                        .constrainAs(respeckCard) { top.linkTo(parent.top, margin = 0.dp) }
                        .fillMaxWidth()
                        .padding(16.dp)
                        .height(IntrinsicSize.Min)
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .systemBarsPadding(),
                    elevation = cardEvaluation
                ) {
                    Column (
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        RespeckTextField()
                        RespeckLiveMatrix()
                        ThingyTextField()
                        ThingyLiveMatrix()
                        DevicePairingButton()
                    }
                }

//                Card (
//                    Modifier
//                        .constrainAs(thingyCard) { bottom.linkTo(parent.bottom, margin = 0.dp) }
//                        .fillMaxWidth()
//                        .padding(16.dp)
//                        .height(IntrinsicSize.Min)
//                        .statusBarsPadding()
//                        .navigationBarsPadding()
//                        .systemBarsPadding(),
//                    elevation = cardEvaluation
//                ) {
//                    Column (
//                        horizontalAlignment = Alignment.CenterHorizontally
//                    ) {
//                        ThingyTextField()
//                        DevicePairingButton()
//                        ThingyLiveMatrix()
////                    ThingyLiveChart()
//                    }
//                }
            }

            Spacer(modifier = Modifier.width(32.dp))
        }
    }

    @Composable
    fun RespeckTextField() {
        val context = LocalContext.current

        val respeckOn = flow {
            context.deviceDataStore.data.map {
                it[booleanPreferencesKey("respeckOn")]
            }.collect {
                if (it != null) {
                    this.emit(it)
                }
            }
        }.collectAsState(initial = false).value

        val respeckLoading = flow {
            context.deviceDataStore.data.map {
                it[booleanPreferencesKey("respeckLoading")]
            }.collect {
                if (it != null) {
                    this.emit(it)
                }
            }
        }.collectAsState(initial = false).value

        AnimatedVisibility(visible = true) {
            Column {
                val localFocusManager = LocalFocusManager.current
                val scope = rememberCoroutineScope()

                val respeckIdDefault = flow {
                    context.deviceDataStore.data.map {
                        it[stringPreferencesKey("respeckId")]
                    }.collect {
                        if (it != null) {
                            this.emit(it)
                        }
                    }
                }.collectAsState(initial = "").value

                var respeckId by remember { mutableStateOf(TextFieldValue(respeckIdDefault)) }

                var showErrorIcon by remember {
                    mutableStateOf(false)
                }

                var showDoneIcon by remember {
                    mutableStateOf(false)
                }

                OutlinedTextField(
                    value = respeckId,
                    modifier = Modifier
                        .padding(8.dp)
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min)
                        .imePadding(),
                    enabled = !(respeckLoading || respeckOn),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (respeckId.text.isNotEmpty() && respeckId.text.matches(Regex("[0-9a-zA-Z]{2}:[0-9a-zA-Z]{2}:[0-9a-zA-Z]{2}:[0-9a-zA-Z]{2}:[0-9a-zA-Z]{2}:[0-9a-zA-Z]{2}"))) {
                                scope.launch {
                                    setRespeckId(context, respeckId.text)
                                }
                            } else {
                                respeckId = TextFieldValue(respeckIdDefault)
                            }
                            showDoneIcon = false
                            showErrorIcon = false
                            localFocusManager.clearFocus()
                        }
                    ),
                    leadingIcon = {
                        Icon(
                            painter = painterResource(id = R.drawable.device),
                            contentDescription = null,
                            tint = if (respeckOn) {Color.Blue} else {Color.Gray}
                        ) },
                    trailingIcon = {
                        if (respeckId.text.isNotEmpty() && respeckId.text.matches(Regex("[0-9a-zA-Z]{2}:[0-9a-zA-Z]{2}:[0-9a-zA-Z]{2}:[0-9a-zA-Z]{2}:[0-9a-zA-Z]{2}:[0-9a-zA-Z]{2}"))) {
                            if (showDoneIcon) {
                                Icon(
                                    painter = painterResource(id = R.drawable.done),
                                    contentDescription = null,
                                    modifier = Modifier.clickable {
                                        scope.launch {
                                            setRespeckId(context, respeckId.text)
                                        }
                                        showDoneIcon = false
                                        showErrorIcon = false
                                        localFocusManager.clearFocus()
                                    },
                                    tint = Color.Blue
                                )
                            }
                        } else {
                            if (showErrorIcon) {
                                Icon(
                                    painter = painterResource(id = R.drawable.error),
                                    contentDescription = null,
                                    tint = Color.Red
                                )
                            }
                        }
                    },
                    label = { Text(text = if (respeckLoading || respeckOn) {
                        respeckIdDefault
                    } else {
                        if (showErrorIcon) { "Invalid Input" } else { "Respeck ID" }
                    }) },
                    placeholder = { Text(text = respeckIdDefault.ifEmpty { "Respeck ID" }) },
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        focusedBorderColor = if (showErrorIcon) {Color.Red} else {Color.Blue},
                        focusedLabelColor = if (showErrorIcon) {Color.Red} else {Color.Blue},
                        unfocusedBorderColor = Color.Gray,
                        unfocusedLabelColor = Color.Gray
                    ),
                    onValueChange = {
                        respeckId = it
                        if (it.text.isNotEmpty() && it.text.matches(Regex("[0-9a-zA-Z]{2}:[0-9a-zA-Z]{2}:[0-9a-zA-Z]{2}:[0-9a-zA-Z]{2}:[0-9a-zA-Z]{2}:[0-9a-zA-Z]{2}"))) {
                            showDoneIcon = true
                            showErrorIcon = false
                        } else {
                            showDoneIcon = false
                            showErrorIcon = true
                        }
                    },
                )
            }
        }
    }

    @Composable
    fun ThingyTextField() {
        val context = LocalContext.current

        val thingyOn = flow {
            context.deviceDataStore.data.map {
                it[booleanPreferencesKey("thingyOn")]
            }.collect {
                if (it != null) {
                    this.emit(it)
                }
            }
        }.collectAsState(initial = false).value

        val thingyLoading = flow {
            context.deviceDataStore.data.map {
                it[booleanPreferencesKey("thingyLoading")]
            }.collect {
                if (it != null) {
                    this.emit(it)
                }
            }
        }.collectAsState(initial = false).value

        AnimatedVisibility(visible = true) {
            Column {
                val localFocusManager = LocalFocusManager.current
                val scope = rememberCoroutineScope()

                val thingyIdDefault = flow {
                    context.deviceDataStore.data.map {
                        it[stringPreferencesKey("thingyId")]
                    }.collect {
                        if (it != null) {
                            this.emit(it)
                        }
                    }
                }.collectAsState(initial = "").value

                var thingyId by remember { mutableStateOf(TextFieldValue(thingyIdDefault)) }

                var showErrorIcon by remember {
                    mutableStateOf(false)
                }

                var showDoneIcon by remember {
                    mutableStateOf(false)
                }

                OutlinedTextField(
                    value = thingyId,
                    modifier = Modifier
                        .padding(8.dp)
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min)
                        .imePadding(),
                    enabled = !(thingyLoading || thingyOn),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (thingyId.text.isNotEmpty() && thingyId.text.matches(Regex("[0-9a-zA-Z]{2}:[0-9a-zA-Z]{2}:[0-9a-zA-Z]{2}:[0-9a-zA-Z]{2}:[0-9a-zA-Z]{2}:[0-9a-zA-Z]{2}"))) {
                                scope.launch {
                                    setThingyId(context, thingyId.text)
                                }
                            } else {
                                thingyId = TextFieldValue(thingyIdDefault)
                            }
                            showDoneIcon = false
                            showErrorIcon = false
                            localFocusManager.clearFocus()
                        }
                    ),
                    leadingIcon = {
                        Icon(
                            painter = painterResource(id = R.drawable.device),
                            contentDescription = null,
                            tint = if (thingyOn) {Color.Blue} else {Color.Gray}
                        ) },
                    trailingIcon = {
                        if (thingyId.text.isNotEmpty() && thingyId.text.matches(Regex("[0-9a-zA-Z]{2}:[0-9a-zA-Z]{2}:[0-9a-zA-Z]{2}:[0-9a-zA-Z]{2}:[0-9a-zA-Z]{2}:[0-9a-zA-Z]{2}"))) {
                            if (showDoneIcon) {
                                Icon(
                                    painter = painterResource(id = R.drawable.done),
                                    contentDescription = null,
                                    modifier = Modifier.clickable {
                                        scope.launch {
                                            setThingyId(context, thingyId.text)
                                        }
                                        showDoneIcon = false
                                        showErrorIcon = false
                                        localFocusManager.clearFocus()
                                    },
                                    tint = Color.Blue
                                )
                            }
                        } else {
                            if (showErrorIcon) {
                                Icon(
                                    painter = painterResource(id = R.drawable.error),
                                    contentDescription = null,
                                    tint = Color.Red
                                )
                            }
                        }
                    },
                    label = { Text(text = if (thingyLoading || thingyOn) {
                        thingyIdDefault
                    } else {
                        if (showErrorIcon) { "Invalid Input" } else { "Thingy ID" }
                    }) },
                    placeholder = { Text(text = thingyIdDefault.ifEmpty { "Thingy ID" }) },
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        focusedBorderColor = if (showErrorIcon) {Color.Red} else {Color.Blue},
                        focusedLabelColor = if (showErrorIcon) {Color.Red} else {Color.Blue},
                        unfocusedBorderColor = Color.Gray,
                        unfocusedLabelColor = Color.Gray
                    ),
                    onValueChange = {
                        thingyId = it
                        if (it.text.isNotEmpty() && it.text.matches(Regex("[0-9a-zA-Z]{2}:[0-9a-zA-Z]{2}:[0-9a-zA-Z]{2}:[0-9a-zA-Z]{2}:[0-9a-zA-Z]{2}:[0-9a-zA-Z]{2}"))) {
                            showDoneIcon = true
                            showErrorIcon = false
                        } else {
                            showDoneIcon = false
                            showErrorIcon = true
                        }
                    },
                )
            }
        }
    }

    @Composable
    fun DevicePairingButton() {
        Row (horizontalArrangement = Arrangement.Center) {
            val context = LocalContext.current
            val scope = rememberCoroutineScope()

            val respeckOn = flow {
                context.deviceDataStore.data.map {
                    it[booleanPreferencesKey("respeckOn")]
                }.collect {
                    if (it != null) {
                        this.emit(it)
                    }
                }
            }.collectAsState(initial = false).value

            val respeckLoading = flow {
                context.deviceDataStore.data.map {
                    it[booleanPreferencesKey("respeckLoading")]
                }.collect {
                    if (it != null) {
                        this.emit(it)
                    }
                }
            }.collectAsState(initial = false).value

            val respeckId = flow {
                context.deviceDataStore.data.map {
                    it[stringPreferencesKey("respeckId")]
                }.collect {
                    if (it != null) {
                        this.emit(it)
                    }
                }
            }.collectAsState(initial = "").value

            val thingyOn = flow {
                context.deviceDataStore.data.map {
                    it[booleanPreferencesKey("thingyOn")]
                }.collect {
                    if (it != null) {
                        this.emit(it)
                    }
                }
            }.collectAsState(initial = false).value

            val thingyLoading = flow {
                context.deviceDataStore.data.map {
                    it[booleanPreferencesKey("thingyLoading")]
                }.collect {
                    if (it != null) {
                        this.emit(it)
                    }
                }
            }.collectAsState(initial = false).value

            val thingyId = flow {
                context.deviceDataStore.data.map {
                    it[stringPreferencesKey("thingyId")]
                }.collect {
                    if (it != null) {
                        this.emit(it)
                    }
                }
            }.collectAsState(initial = "").value

            SSJetPackComposeProgressButton(
                assetColor = if (respeckOn && thingyOn) {Color.Blue.copy(0.7f)} else {colorResource(id = R.color.teal)},
                colors = ButtonDefaults.buttonColors(backgroundColor = Color.White),
                buttonBorderStroke = BorderStroke(0.dp,
                    SolidColor(colorResource(id = R.color.grey))),
                type = SSButtonType.CIRCLE,
                onClick = {
                    if (!respeckLoading && !thingyLoading) {
                        if (!respeckOn && !thingyOn) {
                            scope.launch {
                                setRespeckLoading(context, true)
                                setThingyLoading(context, true)
                                syncAllPairing(context, respeckId, thingyId)
                            }
                        } else {
                            if (thingyOn && respeckOn) {
                                val isServiceRunning = Utils.isServiceRunning(BluetoothSpeckService::class.java, context.applicationContext)
                                val serviceIntent = Intent(context, BluetoothSpeckService::class.java)

                                if (isServiceRunning) {
                                    context.stopService(serviceIntent)
                                }

                                scope.launch {
                                    setRespeckLoading(context, true)
                                    setThingyLoading(context, true)
                                    delay(600)
                                    setRespeckOff(context)
                                    setThingyOff(context)
                                    setRespeckLoading(context, false)
                                    setThingyLoading(context, false)
                                }
                            } else {
                                scope.launch {
                                    syncAllPairing(context, respeckId, thingyId)
                                }
                            }
                        }
                    } else {
                        if (!respeckOn) {
                            scope.launch {
                                setRespeckLoading(context, false)
                            }
                        }

                        if (!thingyOn) {
                            scope.launch {
                                setThingyLoading(context, false)
                            }
                        }
                    }
                },
                buttonState = if (respeckLoading || thingyLoading) {SSButtonState.LOADING} else { if (respeckOn && thingyOn) {SSButtonState.SUCCESS} else {SSButtonState.IDLE}},
                width = 128.dp,
                height = 48.dp,
                padding = PaddingValues(12.dp),
                cornerRadius = 64,
                leftImagePainter = if (respeckOn && thingyOn) {
                    rememberDrawablePainter(drawable = AppCompatResources.getDrawable(
                        LocalContext.current,
                        R.drawable.sensors_off24))

                } else {
                    if (!respeckOn && !thingyOn) {
                        rememberDrawablePainter(drawable = AppCompatResources.getDrawable(
                            LocalContext.current,
                            R.drawable.sensor24))
                    } else {
                        rememberDrawablePainter(drawable = AppCompatResources.getDrawable(
                            LocalContext.current,
                            R.drawable.sync))
                    }
                }
            )
        }
    }

    @Composable
    fun RespeckLiveMatrix() {
        Column {
            val context = LocalContext.current

            val respeckAccX = flow {
                context.deviceDataStore.data.map {
                    it[stringPreferencesKey("respeckAccX")]
                }.collect(collector = {
                    if (it != null) {
                        this.emit(it)
                    }
                })
            }.collectAsState(initial = "0")
            val respeckAccY = flow {
                context.deviceDataStore.data.map {
                    it[stringPreferencesKey("respeckAccY")]
                }.collect(collector = {
                    if (it != null) {
                        this.emit(it)
                    }
                })
            }.collectAsState(initial = "0")
            val respeckAccZ = flow {
                context.deviceDataStore.data.map {
                    it[stringPreferencesKey("respeckAccZ")]
                }.collect(collector = {
                    if (it != null) {
                        this.emit(it)
                    }
                })
            }.collectAsState(initial = "0")

            val respeckGyrX = flow {
                context.deviceDataStore.data.map {
                    it[stringPreferencesKey("respeckGyrX")]
                }.collect(collector = {
                    if (it != null) {
                        this.emit(it)
                    }
                })
            }.collectAsState(initial = "0")
            val respeckGyrY = flow {
                context.deviceDataStore.data.map {
                    it[stringPreferencesKey("respeckGyrY")]
                }.collect(collector = {
                    if (it != null) {
                        this.emit(it)
                    }
                })
            }.collectAsState(initial = "0")
            val respeckGyrZ = flow {
                context.deviceDataStore.data.map {
                    it[stringPreferencesKey("respeckGyrZ")]
                }.collect(collector = {
                    if (it != null) {
                        this.emit(it)
                    }
                })
            }.collectAsState(initial = "0")

            Text(text = "[${respeckAccX.value}, ${respeckAccY.value}, ${respeckAccZ.value}]")
            Text(text = "[${respeckGyrX.value}, ${respeckGyrY.value}, ${respeckGyrZ.value}]")
        }
    }

    @Composable
    fun ThingyLiveMatrix() {
        Column {
            val context = LocalContext.current

            val thingyAccX = flow {
                context.deviceDataStore.data.map {
                    it[stringPreferencesKey("thingyAccX")]
                }.collect(collector = {
                    if (it != null) {
                        this.emit(it)
                    }
                })
            }.collectAsState(initial = "0")
            val thingyAccY = flow {
                context.deviceDataStore.data.map {
                    it[stringPreferencesKey("thingyAccY")]
                }.collect(collector = {
                    if (it != null) {
                        this.emit(it)
                    }
                })
            }.collectAsState(initial = "0")
            val thingyAccZ = flow {
                context.deviceDataStore.data.map {
                    it[stringPreferencesKey("thingyAccZ")]
                }.collect(collector = {
                    if (it != null) {
                        this.emit(it)
                    }
                })
            }.collectAsState(initial = "0")

            val thingyGyrX = flow {
                context.deviceDataStore.data.map {
                    it[stringPreferencesKey("thingyGyrX")]
                }.collect(collector = {
                    if (it != null) {
                        this.emit(it)
                    }
                })
            }.collectAsState(initial = "0")
            val thingyGyrY = flow {
                context.deviceDataStore.data.map {
                    it[stringPreferencesKey("thingyGyrY")]
                }.collect(collector = {
                    if (it != null) {
                        this.emit(it)
                    }
                })
            }.collectAsState(initial = "0")
            val thingyGyrZ = flow {
                context.deviceDataStore.data.map {
                    it[stringPreferencesKey("thingyGyrZ")]
                }.collect(collector = {
                    if (it != null) {
                        this.emit(it)
                    }
                })
            }.collectAsState(initial = "0")

            val thingyMagX = flow {
                context.deviceDataStore.data.map {
                    it[stringPreferencesKey("thingyMagX")]
                }.collect(collector = {
                    if (it != null) {
                        this.emit(it)
                    }
                })
            }.collectAsState(initial = "0")
            val thingyMagY = flow {
                context.deviceDataStore.data.map {
                    it[stringPreferencesKey("thingyMagY")]
                }.collect(collector = {
                    if (it != null) {
                        this.emit(it)
                    }
                })
            }.collectAsState(initial = "0")
            val thingyMagZ = flow {
                context.deviceDataStore.data.map {
                    it[stringPreferencesKey("thingyMagZ")]
                }.collect(collector = {
                    if (it != null) {
                        this.emit(it)
                    }
                })
            }.collectAsState(initial = "0")

            Text(text = "[${thingyAccX.value}, ${thingyAccY.value}, ${thingyAccZ.value}]")
            Text(text = "[${thingyGyrX.value}, ${thingyGyrY.value}, ${thingyGyrZ.value}]")
            Text(text = "[${thingyMagX.value}, ${thingyMagY.value}, ${thingyMagZ.value}]")
        }
    }

    @Composable
    fun ThingyLiveChart() {
        val context = LocalContext.current

        val thingyAccX = flow {
            context.deviceDataStore.data.map {
                it[stringPreferencesKey("thingyAccX")]
            }.collect(collector = {
                if (it != null) {
                    this.emit(it)
                }
            })
        }.collectAsState(initial = "0")

        val thingyAccY = flow {
            context.deviceDataStore.data.map {
                it[stringPreferencesKey("thingyAccY")]
            }.collect(collector = {
                if (it != null) {
                    this.emit(it)
                }
            })
        }.collectAsState(initial = "0")

        val thingyAccZ = flow {
            context.deviceDataStore.data.map {
                it[stringPreferencesKey("thingyAccZ")]
            }.collect(collector = {
                if (it != null) {
                    this.emit(it)
                }
            })
        }.collectAsState(initial = "0")

        val entriesAccX = ArrayList<Entry>()
        entriesAccX.add(Entry(0f, 10f))
        entriesAccX.add(Entry(1f, 8f))
        entriesAccX.add(Entry(2f, 21f))
        entriesAccX.add(Entry(3f, 3f))
        entriesAccX.add(Entry(4f, 9f))
        val entriesAccY = ArrayList<Entry>()
        entriesAccY.add(Entry(0f, 11f))
        entriesAccY.add(Entry(1f, 12f))
        entriesAccY.add(Entry(2f, 13f))
        entriesAccY.add(Entry(3f, 14f))
        entriesAccY.add(Entry(4f, 15f))
        val entriesAccZ = ArrayList<Entry>()
        entriesAccZ.add(Entry(0f, 18f))
        entriesAccZ.add(Entry(1f, 16f))
        entriesAccZ.add(Entry(2f, 14f))
        entriesAccZ.add(Entry(3f, 12f))
        entriesAccZ.add(Entry(4f, 10f))

        val datasetAccX = LineDataSet(entriesAccX, "Accel X")
        val datasetAccY = LineDataSet(entriesAccY, "Accel Y")
        val datasetAccZ = LineDataSet(entriesAccZ, "Accel Z")

        val datasetThingy = ArrayList<ILineDataSet>()
        datasetThingy.add(datasetAccX)
        datasetThingy.add(datasetAccY)
        datasetThingy.add(datasetAccZ)

        val thingyData = LineData(datasetThingy)

        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
            factory = { ctx: Context ->
                val thingyLineChart = LineChart(ctx)

                thingyLineChart.data = thingyData
                thingyLineChart.apply {
                } },
            update = { thingyLineChart ->
                thingyAccX.let {
                    thingyLineChart.data.dataSets[0].setDrawValues(true)
                }
                thingyLineChart.animateXY(1000, 1000)
            }
        )
    }


    @Composable
    fun AccountScreen(innerPadding: PaddingValues) {
//        val context = LocalContext.current
        Login(this)
//        Column(
//            modifier = Modifier
//                .fillMaxSize()
//                .background(colorResource(id = R.color.blue))
//                .wrapContentSize(Alignment.Center)
//                .padding(innerPadding)
//        ) {
//            Text(
//                text = "Add Post Screen",
//                fontWeight = FontWeight.Bold,
//                color = Color.White,
//                modifier = Modifier.align(Alignment.CenterHorizontally),
//                textAlign = TextAlign.Center,
//                fontSize = 20.sp
//            )
//        }
    }

    @Composable
    fun DeviceLiveDataView() {
        Column {
            val context = LocalContext.current

            val respeckAccX = flow {
                context.deviceDataStore.data.map {
                    it[stringPreferencesKey("respeckAccX")]
                }.collect(collector = {
                    if (it != null) {
                        this.emit(it)
                    }
                })
            }.collectAsState(initial = "0")
            val respeckAccY = flow {
                context.deviceDataStore.data.map {
                    it[stringPreferencesKey("respeckAccY")]
                }.collect(collector = {
                    if (it != null) {
                        this.emit(it)
                    }
                })
            }.collectAsState(initial = "0")
            val respeckAccZ = flow {
                context.deviceDataStore.data.map {
                    it[stringPreferencesKey("respeckAccZ")]
                }.collect(collector = {
                    if (it != null) {
                        this.emit(it)
                    }
                })
            }.collectAsState(initial = "0")

            val respeckGyrX = flow {
                context.deviceDataStore.data.map {
                    it[stringPreferencesKey("respeckGyrX")]
                }.collect(collector = {
                    if (it != null) {
                        this.emit(it)
                    }
                })
            }.collectAsState(initial = "0")
            val respeckGyrY = flow {
                context.deviceDataStore.data.map {
                    it[stringPreferencesKey("respeckGyrY")]
                }.collect(collector = {
                    if (it != null) {
                        this.emit(it)
                    }
                })
            }.collectAsState(initial = "0")
            val respeckGyrZ = flow {
                context.deviceDataStore.data.map {
                    it[stringPreferencesKey("respeckGyrZ")]
                }.collect(collector = {
                    if (it != null) {
                        this.emit(it)
                    }
                })
            }.collectAsState(initial = "0")

            val thingyAccX = flow {
                context.deviceDataStore.data.map {
                    it[stringPreferencesKey("thingyAccX")]
                }.collect(collector = {
                    if (it != null) {
                        this.emit(it)
                    }
                })
            }.collectAsState(initial = "0")
            val thingyAccY = flow {
                context.deviceDataStore.data.map {
                    it[stringPreferencesKey("thingyAccY")]
                }.collect(collector = {
                    if (it != null) {
                        this.emit(it)
                    }
                })
            }.collectAsState(initial = "0")
            val thingyAccZ = flow {
                context.deviceDataStore.data.map {
                    it[stringPreferencesKey("thingyAccZ")]
                }.collect(collector = {
                    if (it != null) {
                        this.emit(it)
                    }
                })
            }.collectAsState(initial = "0")

            val thingyGyrX = flow {
                context.deviceDataStore.data.map {
                    it[stringPreferencesKey("thingyGyrX")]
                }.collect(collector = {
                    if (it != null) {
                        this.emit(it)
                    }
                })
            }.collectAsState(initial = "0")
            val thingyGyrY = flow {
                context.deviceDataStore.data.map {
                    it[stringPreferencesKey("thingyGyrY")]
                }.collect(collector = {
                    if (it != null) {
                        this.emit(it)
                    }
                })
            }.collectAsState(initial = "0")
            val thingyGyrZ = flow {
                context.deviceDataStore.data.map {
                    it[stringPreferencesKey("thingyGyrZ")]
                }.collect(collector = {
                    if (it != null) {
                        this.emit(it)
                    }
                })
            }.collectAsState(initial = "0")

            val thingyMagX = flow {
                context.deviceDataStore.data.map {
                    it[stringPreferencesKey("thingyMagX")]
                }.collect(collector = {
                    if (it != null) {
                        this.emit(it)
                    }
                })
            }.collectAsState(initial = "0")
            val thingyMagY = flow {
                context.deviceDataStore.data.map {
                    it[stringPreferencesKey("thingyMagY")]
                }.collect(collector = {
                    if (it != null) {
                        this.emit(it)
                    }
                })
            }.collectAsState(initial = "0")
            val thingyMagZ = flow {
                context.deviceDataStore.data.map {
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

    suspend fun setRespeckId(context: Context, respeckId: String) {
        val respeckIdKey = stringPreferencesKey("respeckId")
        context.deviceDataStore.edit {
            it[respeckIdKey] = respeckId
        }
    }

    suspend fun setRespeckOn(context: Context) {
        val respeckOnKey = booleanPreferencesKey("respeckOn")
        context.deviceDataStore.edit {
            it[respeckOnKey] = true
        }
    }

    suspend fun setRespeckOff(context: Context) {
        val respeckOnKey = booleanPreferencesKey("respeckOn")
        context.deviceDataStore.edit {
            it[respeckOnKey] = false
        }
    }

    suspend fun setRespeckLoading(context: Context, loading: Boolean) {
        val thingyLoadingKey = booleanPreferencesKey("respeckLoading")
        context.deviceDataStore.edit {
            it[thingyLoadingKey] = loading
        }
    }

    suspend fun setRespeckAcc(context: Context, accX: String, accY: String, accZ: String) {
        val respeckAccXKey = stringPreferencesKey("respeckAccX")
        val respeckAccYKey = stringPreferencesKey("respeckAccY")
        val respeckAccZKey = stringPreferencesKey("respeckAccZ")
        context.deviceDataStore.edit {
            it[respeckAccXKey] = accX
            it[respeckAccYKey] = accY
            it[respeckAccZKey] = accZ
        }
    }

    suspend fun setRespeckGyr(context: Context, gyrX: String, gyrY: String, gyrZ: String) {
        val respeckGyrXKey = stringPreferencesKey("respeckGyrX")
        val respeckGyrYKey = stringPreferencesKey("respeckGyrY")
        val respeckGyrZKey = stringPreferencesKey("respeckGyrZ")
        context.deviceDataStore.edit {
            it[respeckGyrXKey] = gyrX
            it[respeckGyrYKey] = gyrY
            it[respeckGyrZKey] = gyrZ
        }
    }

    suspend fun setThingyId(context: Context, thingyId: String) {
        val thingyIdKey = stringPreferencesKey("thingyId")
        context.deviceDataStore.edit {
            it[thingyIdKey] = thingyId
        }
    }

    suspend fun setThingyOn(context: Context) {
        val thingyOnKey = booleanPreferencesKey("thingyOn")
        context.deviceDataStore.edit {
            it[thingyOnKey] = true
        }
    }

    suspend fun setThingyOff(context: Context) {
        val thingyOnKey = booleanPreferencesKey("thingyOn")
        context.deviceDataStore.edit {
            it[thingyOnKey] = false
        }
    }

    suspend fun setThingyLoading(context: Context, loading: Boolean) {
        val thingyLoadingKey = booleanPreferencesKey("thingyLoading")
        context.deviceDataStore.edit {
            it[thingyLoadingKey] = loading
        }
    }

    suspend fun setThingyAcc(context: Context, accX: String, accY: String, accZ: String) {
        val thingyAccXKey = stringPreferencesKey("thingyAccX")
        val thingyAccYKey = stringPreferencesKey("thingyAccY")
        val thingyAccZKey = stringPreferencesKey("thingyAccZ")
        context.deviceDataStore.edit {
            it[thingyAccXKey] = accX
            it[thingyAccYKey] = accY
            it[thingyAccZKey] = accZ
        }
    }

    suspend fun setThingyGyr(context: Context, gyrX: String, gyrY: String, gyrZ: String) {
        val thingyGyrXKey = stringPreferencesKey("thingyGyrX")
        val thingyGyrYKey = stringPreferencesKey("thingyGyrY")
        val thingyGyrZKey = stringPreferencesKey("thingyGyrZ")
        context.deviceDataStore.edit {
            it[thingyGyrXKey] = gyrX
            it[thingyGyrYKey] = gyrY
            it[thingyGyrZKey] = gyrZ
        }
    }

    suspend fun setThingyMag(context: Context, magX: String, magY: String, magZ: String) {
        val thingyMagXKey = stringPreferencesKey("thingyMagX")
        val thingyMagYKey = stringPreferencesKey("thingyMagY")
        val thingyMagZKey = stringPreferencesKey("thingyMagZ")
        context.deviceDataStore.edit {
            it[thingyMagXKey] = magX
            it[thingyMagYKey] = magY
            it[thingyMagZKey] = magZ
        }
    }

    suspend fun startRespeckPairing(context: Context, respeckId: String) {
        val sharedPreferences = context.getSharedPreferences(Constants.PREFERENCES_FILE, Context.MODE_PRIVATE)

        sharedPreferences.edit().putString(
            Constants.RESPECK_MAC_ADDRESS_PREF,
            respeckId
        ).apply()
        sharedPreferences.edit().putInt(Constants.RESPECK_VERSION, 6).apply()

        val isServiceRunning = Utils.isServiceRunning(BluetoothSpeckService::class.java, context.applicationContext)
        val serviceIntent = Intent(context, BluetoothSpeckService::class.java)

        if (!isServiceRunning) {
            context.startService(serviceIntent)
        } else {
            context.stopService(serviceIntent)
            context.startService(serviceIntent)
        }
    }

    suspend fun startThingyPairing(context: Context, thingyId: String) {
        val sharedPreferences = context.getSharedPreferences(Constants.PREFERENCES_FILE, Context.MODE_PRIVATE)

        sharedPreferences.edit().putString(
            Constants.THINGY_MAC_ADDRESS_PREF,
            thingyId
        ).apply()

        val isServiceRunning = Utils.isServiceRunning(BluetoothSpeckService::class.java, context.applicationContext)
        val serviceIntent = Intent(context, BluetoothSpeckService::class.java)

        if (!isServiceRunning) {
            context.startService(serviceIntent)
        } else {
            context.stopService(serviceIntent)
            context.startService(serviceIntent)
        }
    }

    suspend fun syncAllPairing(context: Context, respeckId: String, thingyId: String) {
        val sharedPreferences = context.getSharedPreferences(Constants.PREFERENCES_FILE, Context.MODE_PRIVATE)

        sharedPreferences.edit().putInt(
            Constants.RESPECK_VERSION,
            6
        ).apply()

        sharedPreferences.edit().putString(
            Constants.RESPECK_MAC_ADDRESS_PREF,
            respeckId
        ).apply()

        sharedPreferences.edit().putString(
            Constants.THINGY_MAC_ADDRESS_PREF,
            thingyId
        ).apply()

        val isServiceRunning = Utils.isServiceRunning(BluetoothSpeckService::class.java, context.applicationContext)
        val serviceIntent = Intent(context, BluetoothSpeckService::class.java)

        if (!isServiceRunning) {
            context.startService(serviceIntent)
        } else {
            context.stopService(serviceIntent)
            context.startService(serviceIntent)
        }
    }

    suspend fun setPairState(context: Context, pairState: Boolean) {
        val pairStateKey = booleanPreferencesKey("pairState")
        context.deviceDataStore.edit {
            it[pairStateKey] = pairState
        }
    }

    suspend fun setPred(context: Context, predIC: List<Pair<Int, Float>>) {
        val predKey0I = intPreferencesKey("pred0I")
        val predKey0C = floatPreferencesKey("pred0C")

        val predKey1I = intPreferencesKey("pred1I")
        val predKey1C = floatPreferencesKey("pred1C")

        val predKey2I = intPreferencesKey("pred2I")
        val predKey2C = floatPreferencesKey("pred2C")

        val predKey3I = intPreferencesKey("pred3I")
        val predKey3C = floatPreferencesKey("pred3C")

        val predKey4I = intPreferencesKey("pred4I")
        val predKey4C = floatPreferencesKey("pred4C")

        val predKey5I = intPreferencesKey("pred5I")
        val predKey5C = floatPreferencesKey("pred5C")

        val predKey6I = intPreferencesKey("pred6I")
        val predKey6C = floatPreferencesKey("pred6C")

        val predKey7I = intPreferencesKey("pred7I")
        val predKey7C = floatPreferencesKey("pred7C")

        val predKey8I = intPreferencesKey("pred8I")
        val predKey8C = floatPreferencesKey("pred8C")

        val predKey9I = intPreferencesKey("pred9I")
        val predKey9C = floatPreferencesKey("pred9C")

        val predKey10I = intPreferencesKey("pred10I")
        val predKey10C = floatPreferencesKey("pred10C")

        val predKey11I = intPreferencesKey("pred11I")
        val predKey11C = floatPreferencesKey("pred11C")

        val predKey12I = intPreferencesKey("pred12I")
        val predKey12C = floatPreferencesKey("pred12C")

        val predKey13I = intPreferencesKey("pred13I")
        val predKey13C = floatPreferencesKey("pred13C")

        val mapIC = mapOf(
            0 to Pair(predKey0I, predKey0C),
            1 to Pair(predKey1I, predKey1C),
            2 to Pair(predKey2I, predKey2C),
            3 to Pair(predKey3I, predKey3C),
            4 to Pair(predKey4I, predKey4C),
            5 to Pair(predKey5I, predKey5C),
            6 to Pair(predKey6I, predKey6C),
            7 to Pair(predKey7I, predKey7C),
            8 to Pair(predKey8I, predKey8C),
            9 to Pair(predKey9I, predKey9C),
            10 to Pair(predKey10I, predKey10C),
            11 to Pair(predKey11I, predKey11C),
            12 to Pair(predKey12I, predKey12C),
            13 to Pair(predKey13I, predKey13C),
        )

        context.deviceDataStore.edit {
            it[predKey0I] = predIC[0].first
            it[predKey0C] = predIC[0].second

            it[predKey1I] = predIC[1].first
            it[predKey1C] = predIC[1].second

            it[predKey2I] = predIC[2].first
            it[predKey2C] = predIC[2].second

            it[predKey3I] = predIC[3].first
            it[predKey3C] = predIC[3].second
        }
    }

    suspend fun setPredBasic(context: Context, predIC: List<Pair<Int, Float>>) {
        val predKey0I = intPreferencesKey("predBasic0I")
        val predKey0C = floatPreferencesKey("predBasic0C")

        val predKey1I = intPreferencesKey("predBasic1I")
        val predKey1C = floatPreferencesKey("predBasic1C")

        val predKey2I = intPreferencesKey("predBasic2I")
        val predKey2C = floatPreferencesKey("predBasic2C")

        val predKey3I = intPreferencesKey("predBasic3I")
        val predKey3C = floatPreferencesKey("predBasic3C")

        context.deviceDataStore.edit {
            it[predKey0I] = predIC[0].first
            it[predKey0C] = predIC[0].second

            it[predKey1I] = predIC[1].first
            it[predKey1C] = predIC[1].second

            it[predKey2I] = predIC[2].first
            it[predKey2C] = predIC[2].second

            it[predKey3I] = predIC[3].first
            it[predKey3C] = predIC[3].second
        }
    }

    suspend fun setModelType(context: Context, modelType: Int) {
        val modelTypeKey = intPreferencesKey("modelType")
        context.deviceDataStore.edit {
            it[modelTypeKey] = modelType
        }
    }

    val featureMap = mapOf(
        0 to "Climbing stairs",
        1 to "Descending stairs",
        2 to "Desk work",
        3 to "Sitting",
        4 to "Sitting bent forward",
        5 to "Sitting bent forward",
        6 to "Standing",
        7 to "Lying down left",
        8 to "Lying down on back",
        9 to "Lying down on stomach",
        10 to "Lying down right",
        11 to "Movement",
        12 to "Running",
        13 to "Walking"
    )

    val actList = listOf(
        ActionInfo(0, "Sitting", R.drawable.sitting),
        ActionInfo(1, "Sitting Bent Forward", R.drawable.sitting),
        ActionInfo(2, "Sitting Bent Backward", R.drawable.sitting),
        ActionInfo(3, "Desk work", R.drawable.sitting),
        ActionInfo(4, "Standing", R.drawable.standing),
        ActionInfo(5, "Lying on left", R.drawable.lying),
        ActionInfo(6, "Lying on right", R.drawable.lying),
        ActionInfo(7, "Lying on stomach", R.drawable.lying),
        ActionInfo(8, "Lying on back", R.drawable.lying),
        ActionInfo(9, "Walking", R.drawable.walking),
        ActionInfo(10, "Running", R.drawable.running),
        ActionInfo(11, "Ascending stairs", R.drawable.walking),
        ActionInfo(12, "Descending stairs", R.drawable.walking),
        ActionInfo(13, "Movement", R.drawable.movement),
    )

    companion object {
        const val EXPANSTION_TRANSITION_DURATION = 450
    }
}