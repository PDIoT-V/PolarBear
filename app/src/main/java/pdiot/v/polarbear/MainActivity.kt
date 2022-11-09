package pdiot.v.polarbear

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.*
import androidx.compose.runtime.*
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
import pdiot.v.polarbear.ui.theme.PolarBearTheme



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
            val localFocusManager = LocalFocusManager.current
            var respeckId by remember { mutableStateOf(TextFieldValue("")) }
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

            var thingyId by remember { mutableStateOf(TextFieldValue("")) }
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

            val context = LocalContext.current
            val scope = rememberCoroutineScope()
            OutlinedButton(
                modifier = Modifier
                    .padding(8.dp)
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                onClick = {
                    scope.launch {
                        setSoundAsTrue(context, true)
                    }
                }
            ) {
                Text("Save")
            }

            val userNameKey = booleanPreferencesKey("sound")
            val userName = flow<Boolean> {
                context.dataStore.data.map {
                    it[userNameKey]
                }.collect(collector = {
                    if (it != null) {
                        this.emit(it)
                    }
                })
            }.collectAsState(initial = false)

            OutlinedButton(
                modifier = Modifier
                    .padding(8.dp)
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                onClick = {
                    Toast.makeText(context, userName.value.toString(), Toast.LENGTH_SHORT).show()
                }
            ) {
                Text("Read")
            }
        }
    }
}

suspend fun setSoundAsTrue(context: Context, value: Boolean) {
    val userNameKey = booleanPreferencesKey("sound")
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