package com.ana.theflow

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.ana.theflow.ui.theme.THEFLOWTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            THEFLOWTheme {
                val navController = rememberNavController()

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = "home",
                        modifier = Modifier.padding(innerPadding)
                    ) {
                        composable("home") {
                            HomeScreen(
                                onSearchClick = { navController.navigate("search") },
                                onLoginClick = { navController.navigate("login") },
                                onAddStudioClick = { navController.navigate("createStudio") }
                            )
                        }

                        composable("search") {
                            SearchStudiosScreen(
                                onBackClick = { navController.popBackStack() }
                            )
                        }

                        composable("login") {
                            LoginScreen(
                                onBackClick = { navController.popBackStack() }
                            )
                        }

                        composable("createStudio") {
                            CreateStudioScreen(
                                onBackClick = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HomeScreen(
    onSearchClick: () -> Unit,
    onLoginClick: () -> Unit,
    onAddStudioClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "THE FLOW",
            fontSize = 36.sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onSearchClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("חיפוש סטודיואים")
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = onLoginClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("התחברות")
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = onAddStudioClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("הוספת סטודיו")
        }
    }
}

@Composable
fun SearchStudiosScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var location by remember { mutableStateOf("") }
    var danceStyle by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "חיפוש סטודיואים",
            fontSize = 28.sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = location,
            onValueChange = { location = it },
            label = { Text("עיר / אזור") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = danceStyle,
            onValueChange = { danceStyle = it },
            label = { Text("סגנון ריקוד") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = {
                // בהמשך נחבר כאן חיפוש מול Firebase Firestore
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("חפש")
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = onBackClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("חזרה")
        }
    }
}

@Composable
fun LoginScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "התחברות",
            fontSize = 28.sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("אימייל") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("סיסמה") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = {
                // בהמשך נחבר כאן Firebase Authentication
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("התחברי")
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = onBackClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("חזרה")
        }
    }
}

@Composable
fun CreateStudioScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var studioName by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var danceStyles by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "הוספת סטודיו",
            fontSize = 28.sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = studioName,
            onValueChange = { studioName = it },
            label = { Text("שם הסטודיו") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = address,
            onValueChange = { address = it },
            label = { Text("כתובת") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = danceStyles,
            onValueChange = { danceStyles = it },
            label = { Text("סגנונות ריקוד") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = phone,
            onValueChange = { phone = it },
            label = { Text("טלפון") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = {
                // בהמשך נשמור את הסטודיו ב-Firebase Firestore עם status = pending
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("שליחת סטודיו לאישור")
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = onBackClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("חזרה")
        }
    }
}

@Preview(showBackground = true)
@Composable
fun HomeScreenPreview() {
    THEFLOWTheme {
        HomeScreen(
            onSearchClick = {},
            onLoginClick = {},
            onAddStudioClick = {}
        )
    }
}
