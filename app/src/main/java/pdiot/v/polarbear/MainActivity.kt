package pdiot.v.polarbear

import android.Manifest
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
import androidx.appcompat.content.res.AppCompatResources
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.InternalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
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
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import pdiot.v.polarbear.bluetooth.BluetoothSpeckService
import pdiot.v.polarbear.bluetooth.ConnectingActivity
import pdiot.v.polarbear.ui.theme.PolarBearTheme
import pdiot.v.polarbear.utils.Constants
import pdiot.v.polarbear.utils.RESpeckLiveData
import pdiot.v.polarbear.utils.ThingyLiveData
import pdiot.v.polarbear.utils.Utils
import kotlin.system.exitProcess


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
                        setRespeckOn(this@MainActivity)
                        setRespeckAcc(this@MainActivity,
                            "${respeckLiveData.accelX}",
                            "${respeckLiveData.accelY}",
                            "${respeckLiveData.accelZ}")
                        setRespeckGyr(this@MainActivity,
                            "${respeckLiveData.gyro.x}",
                            "${respeckLiveData.gyro.y}",
                            "${respeckLiveData.gyro.z}")
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

                    Log.d("Live", "onReceive: thingyLiveData = $thingyLiveData")

                    lifecycleScope.launch {
                        setThingyOn(this@MainActivity)
                        setThingyLoading(this@MainActivity, false)
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
                    AppScreen()
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
            setThingyLoading(this@MainActivity, true)
        }
    }

    private fun resetDeviceStates() {
        lifecycleScope.launch {
            setRespeckOff(this@MainActivity)
            setThingyOff(this@MainActivity)
        }
    }
}

@Composable
fun AppScreen() {
    val navController = rememberNavController ()
    Scaffold (
        topBar = {  },
        floatingActionButtonPosition = FabPosition.End,
        floatingActionButton = { FloatingPairingButton() },
        bottomBar = { BottomNavigation (navController = navController) }
    ) { paddingValues ->
        NavigationGraph (navController = navController, innerPadding = paddingValues)
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
                if (thingyOn && respeckOn) {
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

@OptIn(ExperimentalFoundationApi::class, ExperimentalAnimationApi::class)
@Composable
fun HomeScreen(innerPadding: PaddingValues) {
    val mainViewModel: CountViewModel = viewModel()
    val seconds by mainViewModel.seconds.collectAsState(initial = "00")

    Column (
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            ) {
        Card (
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)) {
            var actionList by remember {
                mutableStateOf(listOf(
                    ActionInfo(0, "Sitting Straight", R.drawable.sitting),
                    ActionInfo(1, "Sitting Bent Forward", R.drawable.sitting),
                    ActionInfo(2, "Sitting Bent Backward", R.drawable.sitting),
                    ActionInfo(3, "Desk work", R.drawable.sitting),
                    ActionInfo(4, "Standing", R.drawable.standing),
                    ActionInfo(5, "Lying down on the left side", R.drawable.lying),
                    ActionInfo(6, "Lying down on the right side", R.drawable.lying),
                    ActionInfo(7, "Lying down on the front", R.drawable.lying),
                    ActionInfo(8, "Lying down on the back", R.drawable.lying),
                    ActionInfo(9, "Walking", R.drawable.walking),
                    ActionInfo(10, "Running", R.drawable.running),
                    ActionInfo(11, "Ascending stairs", R.drawable.walking),
                    ActionInfo(12, "Descending stairs", R.drawable.walking),
                    ActionInfo(13, "General movement", R.drawable.movement),
                ))
            }


            LazyColumn {
                items(actionList.slice(0..4), key = {it.id}) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                actionList = actionList
                                    .shuffled()
                                    .slice(0..4)
                            }
                            .animateItemPlacement()
                            .padding(16.dp, 16.dp, 16.dp, 16.dp)) {
                        Icon(painterResource(id = it.icon), it.name)
                        Column (modifier = Modifier.padding(16.dp, 0.dp, 16.dp, 0.dp)) {
                            val actionCategory = if (it.id in listOf(0, 1, 2, 3)) { "sitting" }
                            else { if (it.id == 4) { "standing" }
                            else { if (it.id in listOf(5, 6, 7, 8)) { "lying" }
                            else { if (it.id == 9) { "walking" }
                            else { if (it.id == 10) { "running" }
                            else { if (it.id in listOf(11, 12)) { "walking" }
                            else { if (it.id == 13) { "movement" }
                            else { "" } } } } } } }
                            Text(text = actionCategory)
                            LinearProgressIndicator()
                        }
                        Text(text = "${it.id}")
                    }
                }
            }
        }

        AnimatedContent(
            targetState = seconds,
            transitionSpec = {
                addAnimation().using(
                    SizeTransform(clip = false)
                )
            }
        ) { targetCount ->
            Text(
                text = "$targetCount",
                style = TextStyle(fontSize = MaterialTheme.typography.h1.fontSize),
                textAlign = TextAlign.Center
            )
        }

        Column (
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressbar3()
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
//    DeviceIdTextField("E7:6E:9C:24:55:9A", "DF:80:AA:B3:5A:F7")
    Column (
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Gray)
            .padding(innerPadding),
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
                Column {
                    RespeckTextField()
                    RespeckLiveMatrix()
                }
            }

            Card (
                Modifier
                    .constrainAs(thingyCard) { bottom.linkTo(parent.bottom, margin = 0.dp) }
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
                    ThingyTextField()
                    ThingyPairingButton()
                    ThingyLiveMatrix()
                    ThingyLiveChart()
                }
            }
        }

        Spacer(modifier = Modifier.width(32.dp))
    }
}

@Composable
fun AccountScreen(innerPadding: PaddingValues) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colorResource(id = R.color.blue))
            .wrapContentSize(Alignment.Center)
            .padding(innerPadding)
    ) {
        Text(
            text = "Add Post Screen",
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.align(Alignment.CenterHorizontally),
            textAlign = TextAlign.Center,
            fontSize = 20.sp
        )
    }
}

@Composable
fun NavigationGraph(navController: NavHostController, innerPadding: PaddingValues) {
    NavHost(navController, startDestination = BottomNavItem.Home.screenRoute) {
        composable(BottomNavItem.Home.screenRoute) {
            HomeScreen(innerPadding)
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
fun DeviceIdTextField(defaultRespeckId: String, defaultThingyId: String) {
    Card (
        Modifier
            .fillMaxWidth()
            .padding(20.dp)
            .height(IntrinsicSize.Min),
        elevation = 10.dp,
    ) {
        Column {
            val context = LocalContext.current
            val localFocusManager = LocalFocusManager.current
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

            val thingyOn = flow {
                context.deviceDataStore.data.map {
                    it[booleanPreferencesKey("thingyOn")]
                }.collect {
                    if (it != null) {
                        this.emit(it)
                    }
                }
            }.collectAsState(initial = false).value

            val animationDuration = 2000

            AnimatedVisibility(
                visible = !(thingyOn && respeckOn),
                enter = fadeIn(animationSpec = tween(durationMillis = animationDuration)),
                exit = fadeOut(animationSpec = tween(durationMillis = animationDuration))
            ) {
                Column (Modifier.height(IntrinsicSize.Min)) {
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
                            scope.launch {
                                setRespeckId(context, it.text)
                            }
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Next
                        ),
                        keyboardActions = KeyboardActions(
                            onNext = { localFocusManager.moveFocus(FocusDirection.Down) }
                        )
                    )

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
                            scope.launch {
                                setThingyId(context, it.text)
                            }
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = { localFocusManager.clearFocus() }
                        )
                    )

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
                }
            }

            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

            DeviceLiveDataView()
        }
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

    AnimatedVisibility(visible = !respeckOn) {
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

            var showNoticeMsg by remember {
                mutableStateOf(false)
            }

            var showDoneIcon by remember {
                mutableStateOf(false)
            }

            var respeckId by remember { mutableStateOf(TextFieldValue(respeckIdDefault)) }
            OutlinedTextField(
                modifier = Modifier
                    .padding(8.dp)
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                value = respeckId,
                label = { Text(text = if (respeckOn) {
                    respeckIdDefault
                } else { "Respeck ID" }) },
                placeholder = { Text(text = respeckIdDefault.ifEmpty { "Respeck ID" }) },
                onValueChange = {
                    respeckId = it
                    showDoneIcon = true
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = {
                        if (respeckId.text.isNotEmpty()) {
                            if (respeckId.text.matches(Regex("[0-9a-zA-Z]{2}:[0-9a-zA-Z]{2}:[0-9a-zA-Z]{2}:[0-9a-zA-Z]{2}:[0-9a-zA-Z]{2}:[0-9a-zA-Z]{2}"))) {
                                scope.launch {
                                    setRespeckId(context, respeckId.text)
                                }
                            }
                        }
                        localFocusManager.moveFocus(FocusDirection.Down)
                    }
                ),
                leadingIcon = {
                    Icon(
                        painter = painterResource(id = R.drawable.device),
                        contentDescription = null
                    ) },
                trailingIcon = {
                    if (respeckId.text.isNotEmpty()) {
                        if (respeckId.text.matches(Regex("[0-9a-zA-Z]{2}:[0-9a-zA-Z]{2}:[0-9a-zA-Z]{2}:[0-9a-zA-Z]{2}:[0-9a-zA-Z]{2}:[0-9a-zA-Z]{2}"))) {
                            if (showDoneIcon) {
                                Icon(
                                    painter = painterResource(id = R.drawable.done),
                                    contentDescription = null,
                                    modifier = Modifier.clickable {
                                        scope.launch {
                                            setRespeckId(context, respeckId.text)
                                        }
                                        localFocusManager.moveFocus(FocusDirection.Down)
                                        showDoneIcon = false
                                    },
                                    tint = Color.Green
                                )
                            }

                        } else {
                            Icon(
                                painter = painterResource(id = R.drawable.error),
                                contentDescription = null,
                                modifier = Modifier.clickable {
                                    showNoticeMsg = true
                                },
                                tint = Color.Red
                            )
                        }
                    }}
            )
            AnimatedVisibility(visible = showNoticeMsg) {
                Text(text = "Invalid Respeck ID.", color = Color.Red)
            }
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
fun ThingyPairingButton() {
    Row (horizontalArrangement = Arrangement.Center) {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()

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
            assetColor = if (!thingyOn) {colorResource(id = R.color.teal)} else {colorResource(id = R.color.blue)},
            colors = ButtonDefaults.buttonColors(backgroundColor = Color.White),
            buttonBorderStroke = BorderStroke(0.dp,
                SolidColor(colorResource(id = R.color.grey))),
            type = SSButtonType.CIRCLE,
            onClick = {
                if (!thingyLoading) {
                    if (!thingyOn) {
                        val sharedPreferences = context.getSharedPreferences(Constants.PREFERENCES_FILE, Context.MODE_PRIVATE)

                        sharedPreferences.edit().putString(
                            Constants.THINGY_MAC_ADDRESS_PREF,
                            thingyId
                        ).apply()

                        val isServiceRunning = Utils.isServiceRunning(BluetoothSpeckService::class.java, context.applicationContext)

                        if (!isServiceRunning) {
                            context.startService(Intent(context, BluetoothSpeckService::class.java))
                        }

                        scope.launch {
                            setThingyLoading(context, true)
                        }
                    } else {
                        val isServiceRunning = Utils.isServiceRunning(BluetoothSpeckService::class.java, context.applicationContext)

                        if (isServiceRunning) {
                            val serviceIntent = Intent(context, BluetoothSpeckService::class.java)
                            context.stopService(serviceIntent)
                        }

                        scope.launch {
                            setThingyLoading(context, true)
                            delay(600)
                            setThingyOff(context)
                            setThingyLoading(context, false)
                        }
                    }
                } else {
                    if (!thingyOn) {
                        scope.launch {
                            setThingyLoading(context, false)
                        }
                    }
                }
            },
            buttonState = if (thingyLoading) {SSButtonState.LOADING} else { if (thingyOn) {SSButtonState.SUCCESS} else {SSButtonState.IDLE}},
            width = 128.dp,
            height = 48.dp,
            padding = PaddingValues(12.dp),
            cornerRadius = 64,
            leftImagePainter = if (!thingyOn) {
                rememberDrawablePainter(drawable = AppCompatResources.getDrawable(
                    LocalContext.current,
                    R.drawable.sensor24))
            } else {
                rememberDrawablePainter(drawable = AppCompatResources.getDrawable(
                    LocalContext.current,
                    R.drawable.sensors_off24))
            }
        )
    }
}

@Composable
fun RespeckLiveMatrix() {
    Column {
        val context = LocalContext.current

        val respeckId = flow {
            context.deviceDataStore.data.map {
                it[stringPreferencesKey("respeckId")]
            }.collect {
                if (it != null) {
                    this.emit(it)
                }
            }
        }.collectAsState(initial = "")

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

        Text(text = respeckId.value)
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
            thingyLineChart
    }) {
        thingyData.notifyDataChanged()
        it.notifyDataSetChanged()
        it.invalidate()
        it.setVisibleXRangeMaximum(150f)
        it.moveViewToX(it.lowestVisibleX + 40)

        setData()
    }
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

    sharedPreferences.edit().putString(
        Constants.RESPECK_MAC_ADDRESS_PREF,
        respeckId
    ).apply()
    sharedPreferences.edit().putInt(Constants.RESPECK_VERSION, 6).apply()

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

@InternalTextApi
@Preview
@Composable
fun DefaultPreview() {
    DeviceIdTextField("E7:6E:9C:24:55:9A", "DF:80:AA:B3:5A:F7")
}