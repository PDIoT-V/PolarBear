package pdiot.v.polarbear.login

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Alignment
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.*
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat.startActivity
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.launch
import pdiot.v.polarbear.R
import pdiot.v.polarbear.deviceDataStore

class MyPageActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState);
        setContent {
            Scaffold (
                content = {
                    CreateProfileCard(it)
                    Logout(this, it)
                }
            )
        }
    }
}

@Composable
private fun CreateImageProfile(modifier: Modifier = Modifier) {
    Surface(
        modifier = Modifier
            .size(300.dp)
            .padding(15.dp),
        shape = CircleShape,
        border = BorderStroke(0.5.dp, Color.LightGray),
        elevation = 4.dp,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
    ) {
        Image(
            painter = painterResource(id = R.drawable.user_profile),
            contentDescription = "profile image",
            modifier = modifier.size(135.dp),
            contentScale = ContentScale.Crop
        )
    }
}

@Composable
private fun CreateInfo() {
    val user = Firebase.auth.currentUser
    val email = user?.email

    Column(
        modifier = Modifier
            .padding(25.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "User email:",
            modifier = Modifier.padding(3.dp),
            color = MaterialTheme.colorScheme.primary,
            fontSize = 25.sp,
            style = MaterialTheme.typography.labelMedium,
            fontFamily = FontFamily(Font(R.font.jost_book, FontWeight.Normal)),
        )
        Text(
            color = MaterialTheme.colorScheme.primary,
            fontSize = 25.sp,
            style = MaterialTheme.typography.labelMedium,
            fontFamily = FontFamily(Font(R.font.jost_medium, FontWeight.Normal)),
            text = email.toString(),
        )
    }
}


@Composable
fun CreateProfileCard(paddingValues: PaddingValues) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
    )
    {
        Card(
            modifier = Modifier
                .width(200.dp)
                .height(390.dp)
                .padding(12.dp),
            shape = RoundedCornerShape(corner = CornerSize(15.dp)),
            backgroundColor = Color.White,
            elevation = 4.dp
        )
        {
            Column(
                modifier = Modifier.height(300.dp),
                verticalArrangement = Arrangement.Top,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CreateImageProfile()
                Divider()
                CreateInfo()
            }
        }
    }
//TODO: boolean, login true, vv.
}

@Composable
fun Logout(context: ComponentActivity, paddingValues: PaddingValues) {
    val scope = rememberCoroutineScope()
    val loContext = LocalContext.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .background(
                color = Color.Transparent,
            )
    ) {
        Box(
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Spacer(modifier = Modifier.padding(100.dp))
            val gradientColors = listOf(Color(0xFF484BF1), Color(0xFF673AB7))
            val cornerRadius = 16.dp
            val roundedCornerShape = RoundedCornerShape(topStart = 30.dp, bottomEnd = 30.dp)
            androidx.compose.material3.Button(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 32.dp, end = 32.dp),
                onClick = {
                    Firebase.auth.signOut()
                    scope.launch {
                        setLogOut(loContext)
                    }
                },
                contentPadding = PaddingValues(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Transparent
                ),
                shape = RoundedCornerShape(cornerRadius)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            brush = Brush.horizontalGradient(colors = gradientColors),
                            shape = roundedCornerShape
                        )
                        .clip(roundedCornerShape)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.material3.Text(
                        text = "Logout",
                        fontSize = 20.sp,
                        color = Color.White,
                        fontFamily = FontFamily(Font(R.font.jost_book, FontWeight.Normal)),
                    )
                }
            }
        }
    }
}



suspend fun setLogOut(context: Context) {
    context.deviceDataStore.edit {
        it[booleanPreferencesKey("loggedIn")] = false
    }
}
