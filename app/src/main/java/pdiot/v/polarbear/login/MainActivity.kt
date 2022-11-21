package pdiot.v.polarbear.login

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Scaffold
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase



//class MainActivity : ComponentActivity() {
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState);
//        val user = Firebase.auth.currentUser
//        val email = user?.email
//        setContent {
//            Scaffold (
//                content = {
//                    if (email.toString() != "") {
//                        this.startActivity(Intent(this, MyPageActivity::class.java))
//                    } else {
//                        Login(this)
//                    }
//                }
//            )
//        }
//    }
//}