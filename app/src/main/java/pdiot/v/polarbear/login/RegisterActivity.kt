package pdiot.v.polarbear.login

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
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
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import pdiot.v.polarbear.R

class RegisterActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState);
        setContent {
            Scaffold (
                content = {
                    Register(this, it)
                }
            )
        }
    }
}

@Composable
fun Register(context: ComponentActivity, paddingValues: PaddingValues) {
    val auth = Firebase.auth
    val emailValue = remember { mutableStateOf("") }
    val passwordValue = remember { mutableStateOf("") }
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
            Image(
                painter = painterResource(id = R.drawable.user_reg),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .height(400.dp)
                    .fillMaxWidth(),
                )
            Column(
                modifier = Modifier.padding(40.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                //.........................Spacer
                Spacer(modifier = Modifier.height(170.dp))
                //.........................Text: title
                androidx.compose.material3.Text(
                    text = "Register",
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .padding(top = 160.dp)
                        .fillMaxWidth(),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontFamily = FontFamily(Font(R.font.jost_medium, FontWeight.Normal)),
                )
                Spacer(modifier = Modifier.padding(8.dp))
                OutlinedTextField(
                    label = {
                        Text("Email Address",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelMedium,
                        )
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    value = emailValue.value,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(topEnd =12.dp, bottomStart =12.dp),
                    placeholder = { Text(text = "Email Address") },
                    leadingIcon = {
                        Image(
                            painterResource(R.drawable.ic_email),
                            contentDescription = "",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .height(25.dp)
                                .width(25.dp)
                        )
                    },
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.primary),
                    onValueChange = {
                        emailValue.value = it
                    }
                )
                Spacer(modifier = Modifier.padding(3.dp))
                OutlinedTextField(
                    label = {
                        Text("Enter Password",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelMedium,
                        )
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    value = passwordValue.value,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(topEnd =12.dp, bottomStart =12.dp),
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.primary),
                    onValueChange = {
                        passwordValue.value = it
                    },
                    visualTransformation = PasswordVisualTransformation(),
                    placeholder = { Text(text = "Password") },
                    leadingIcon = {
                        Image(
                            painterResource(R.drawable.ic_lock),
                            contentDescription = "",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .height(25.dp)
                                .width(25.dp)
                        )
                    }
                )

                val gradientColors = listOf(Color(0xFF484BF1), Color(0xFF673AB7))
                val cornerRadius = 16.dp
                val nameButton = "Create An Account"
                val roundedCornerShape = RoundedCornerShape(topStart = 30.dp,bottomEnd = 30.dp)
                Spacer(modifier = Modifier.padding(10.dp))
                androidx.compose.material3.Button(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 32.dp, end = 32.dp),
                    onClick = {
                        if (emailValue.value.isNotEmpty() && passwordValue.value.isNotEmpty()){
                            auth.createUserWithEmailAndPassword(
                                emailValue.value.trim(),
                                passwordValue.value.trim()
                            )
                                .addOnCompleteListener(context) { task ->
                                    if (task.isSuccessful) {
                                        Log.d("AUTH", "Register Success!")
//                                        context.startActivity(Intent(context, MyPageActivity::class.java))
                                    } else {
                                        Log.d("Auth", "Failed: ${task.exception}")
                                        Toast.makeText(loContext, "Email or password input Wrong", Toast.LENGTH_SHORT).show()
                                    }
                                }
                        } else {
                            Log.d("INPUT", "Please input email or password")
                            Toast.makeText(loContext, "Email or password is Empty", Toast.LENGTH_SHORT).show()
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
                            text = nameButton,
                            fontSize = 20.sp,
                            color = Color.White,
                            fontFamily = FontFamily(Font(R.font.jost_book, FontWeight.Normal)),
                        )
                    }
                }

                Spacer(modifier = Modifier.padding(10.dp))
                androidx.compose.material3.TextButton(
                    onClick = {
//                        context.startActivity(Intent(context, MainActivity::class.java))
                    }
                ) {
                    androidx.compose.material3.Text(
                        text = "Sign In",
                        letterSpacing = 1.sp,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                Spacer(modifier = Modifier.padding(5.dp))
                androidx.compose.material3.TextButton(
                    onClick = {
                        context.startActivity(Intent(context, ResetActivity::class.java))
                    }
                ) {
                    androidx.compose.material3.Text(
                        text = "Reset Password",
                        letterSpacing = 1.sp,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                Spacer(modifier = Modifier.padding(20.dp))
            }
        }
    }
}