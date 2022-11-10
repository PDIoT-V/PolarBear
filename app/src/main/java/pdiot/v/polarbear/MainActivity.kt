package pdiot.v.polarbear

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.core.content.ContextCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import pdiot.v.polarbear.bluetooth.ConnectingActivity
import pdiot.v.polarbear.ui.theme.PolarBearTheme

private fun PackageManager.missingSystemFeature(name: String): Boolean = !hasSystemFeature(name)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PolarBearTheme {
                // A surface container using the 'background' color from the theme
                Surface(color = MaterialTheme.colors.background) {
                    DeviceIdTextField()
                }
            }
        }


    }
}


@Composable
fun DeviceIdTextField() {
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
            val scope = rememberCoroutineScope()

            var respeckId by remember { mutableStateOf(TextFieldValue("E7:6E:9C:24:55:9A")) }
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

            Text(text = respeckId.text,
                modifier = Modifier.align(alignment = Alignment.CenterHorizontally))

            var thingyId by remember { mutableStateOf(TextFieldValue("DF:80:AA:B3:5A:F7")) }
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

            Text(text = thingyId.text,
                modifier = Modifier.align(alignment = Alignment.CenterHorizontally), )

            val userNameKey = stringPreferencesKey("sound")
            val userName = flow<String> {
                context.dataStore.data.map {
                    it[userNameKey]
                }.collect(collector = {
                    if (it != null) {
                        this.emit(it)
                    }
                })
            }.collectAsState(initial = "")

            OutlinedButton(
                modifier = Modifier
                    .padding(8.dp)
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                onClick = {
                    scope.launch {
                        setSoundAsTrue(context, respeckId.text)
                    }
                }
            ) {
                Text("Save")
            }

            OutlinedButton(
                modifier = Modifier
                    .padding(8.dp)
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                onClick = {
                    Toast.makeText(context, userName.value, Toast.LENGTH_SHORT).show()
                }
            ) {
                Text("Read")
            }

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
}

suspend fun setSoundAsTrue(context: Context, value: String) {
    val userNameKey = stringPreferencesKey("sound")
    context.dataStore.edit {
        it[userNameKey] = value
    }
}

@InternalTextApi
@Preview
@Composable
fun DefaultPreview() {
    DeviceIdTextField()
}