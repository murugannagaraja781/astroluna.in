package com.astroluna.ui.astro

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.astroluna.data.api.ApiClient
import com.astroluna.data.model.AstroRegistration
import com.astroluna.ui.theme.CosmicAppTheme
import kotlinx.coroutines.launch

class AstrologerRegistrationActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CosmicAppTheme {
                AstrologerRegistrationScreen(onBack = { finish() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AstrologerRegistrationScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(false) }

    // Form State
    var realName by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var gender by remember { mutableStateOf("") }
    var dob by remember { mutableStateOf("") }
    var tob by remember { mutableStateOf("") }
    var pob by remember { mutableStateOf("") }
    var cell1 by remember { mutableStateOf("") }
    var cell2 by remember { mutableStateOf("") }
    var whatsapp by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var aadhar by remember { mutableStateOf("") }
    var pan by remember { mutableStateOf("") }
    var experience by remember { mutableStateOf("") }
    var profession by remember { mutableStateOf("") }
    var bankDetails by remember { mutableStateOf("") }
    var upiName by remember { mutableStateOf("") }
    var upiNumber by remember { mutableStateOf("") }

    val navy = Color(0xFF000B18)
    val royalBlue = Color(0xFF001F3F)
    val cyan = Color(0xFF7FDBFF)

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Astrologer Registration", color = cyan, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = cyan)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = navy)
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Brush.verticalGradient(listOf(navy, royalBlue)))
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 32.dp, top = 16.dp)
            ) {
                item { SectionTitle("Basic Information") }
                item { CustomTextField(value = realName, onValueChange = { realName = it }, label = "Real Name *") }
                item { CustomTextField(value = displayName, onValueChange = { displayName = it }, label = "Display Name") }
                item { CustomTextField(value = gender, onValueChange = { gender = it }, label = "Gender (Male/Female)") }

                item { SectionTitle("Birth Details") }
                item {
                    val datePickerDialog = android.app.DatePickerDialog(
                        context,
                        { _, year, month, dayOfMonth ->
                            dob = "$year-${String.format("%02d", month + 1)}-${String.format("%02d", dayOfMonth)}"
                        },
                        1990, 0, 1
                    )
                    ReadOnlyTextField(
                        value = dob,
                        onClick = { datePickerDialog.show() },
                        label = "Date of Birth *"
                    )
                }
                item {
                    val timePickerDialog = android.app.TimePickerDialog(
                        context,
                        { _, hourOfDay, minute ->
                            tob = "${String.format("%02d", hourOfDay)}:${String.format("%02d", minute)}"
                        },
                        12, 0, true
                    )
                    ReadOnlyTextField(
                        value = tob,
                        onClick = { timePickerDialog.show() },
                        label = "Time of Birth *"
                    )
                }
                item { CustomTextField(value = pob, onValueChange = { pob = it }, label = "Place of Birth *") }

                item { SectionTitle("Contact Details") }
                item { CustomTextField(value = cell1, onValueChange = { cell1 = it }, label = "Mobile Number 1 *", keyboardType = KeyboardType.Phone) }
                item { CustomTextField(value = cell2, onValueChange = { cell2 = it }, label = "Mobile Number 2", keyboardType = KeyboardType.Phone) }
                item { CustomTextField(value = whatsapp, onValueChange = { whatsapp = it }, label = "WhatsApp Number", keyboardType = KeyboardType.Phone) }
                item { CustomTextField(value = email, onValueChange = { email = it }, label = "Email Address", keyboardType = KeyboardType.Email) }
                item { CustomTextField(value = address, onValueChange = { address = it }, label = "Full Address", singleLine = false) }

                item { SectionTitle("Professional Details") }
                item { CustomTextField(value = aadhar, onValueChange = { aadhar = it }, label = "Aadhar Number") }
                item { CustomTextField(value = pan, onValueChange = { pan = it }, label = "PAN Number") }
                item { CustomTextField(value = experience, onValueChange = { experience = it }, label = "Astrology Experience (Years)") }
                item { CustomTextField(value = profession, onValueChange = { profession = it }, label = "Current Profession") }

                item { SectionTitle("Payment Details") }
                item { CustomTextField(value = bankDetails, onValueChange = { bankDetails = it }, label = "Bank Details (A/C, IFSC)", singleLine = false) }
                item { CustomTextField(value = upiName, onValueChange = { upiName = it }, label = "UPI Name") }
                item { CustomTextField(value = upiNumber, onValueChange = { upiNumber = it }, label = "UPI Number / ID") }

                item {
                    Button(
                        onClick = {
                            if (realName.isBlank() || cell1.isBlank()) {
                                Toast.makeText(context, "Please fill required fields (*)", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            isLoading = true
                            val regData = AstroRegistration(
                                realName = realName,
                                displayName = if(displayName.isBlank()) null else displayName,
                                gender = if(gender.isBlank()) null else gender,
                                dob = if(dob.isBlank()) null else dob,
                                tob = if(tob.isBlank()) null else tob,
                                pob = if(pob.isBlank()) null else pob,
                                cellNumber1 = cell1,
                                cellNumber2 = if(cell2.isBlank()) null else cell2,
                                whatsAppNumber = if(whatsapp.isBlank()) null else whatsapp,
                                email = if(email.isBlank()) null else email,
                                address = if(address.isBlank()) null else address,
                                aadharNumber = if(aadhar.isBlank()) null else aadhar,
                                panNumber = if(pan.isBlank()) null else pan,
                                astrologyExperience = if(experience.isBlank()) null else experience,
                                profession = if(profession.isBlank()) null else profession,
                                bankDetails = if(bankDetails.isBlank()) null else bankDetails,
                                upiName = if(upiName.isBlank()) null else upiName,
                                upiNumber = if(upiNumber.isBlank()) null else upiNumber
                            )

                            scope.launch {
                                try {
                                    if (dob.isBlank() || tob.isBlank() || pob.isBlank()) {
                                        Toast.makeText(context, "Birth details are required", Toast.LENGTH_SHORT).show()
                                        isLoading = false
                                        return@launch
                                    }
                                    val response = ApiClient.api.registerAstrologer(regData)
                                    if (response.isSuccessful) {
                                        Toast.makeText(context, "Application submitted successfully!", Toast.LENGTH_LONG).show()
                                        onBack()
                                    } else {
                                        Toast.makeText(context, "Failed: ${response.code()}", Toast.LENGTH_SHORT).show()
                                    }
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                                } finally {
                                    isLoading = false
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = cyan, contentColor = Color.Black),
                        shape = RoundedCornerShape(12.dp),
                        enabled = !isLoading
                    ) {
                        if (isLoading) CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(24.dp))
                        else Text("Submit Application", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun SectionTitle(title: String) {
    Text(
        text = title,
        color = Color(0xFF7FDBFF),
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadOnlyTextField(
    value: String,
    onClick: () -> Unit,
    label: String
) {
    OutlinedTextField(
        value = value,
        onValueChange = {},
        label = { Text(label, color = Color.LightGray) },
        modifier = Modifier.fillMaxWidth(),
        enabled = false, // Disable to make clickable via box or surface wrapper if needed? No, let's keep it simple
        readOnly = true,
        colors = TextFieldDefaults.outlinedTextFieldColors(
            disabledBorderColor = Color.LightGray.copy(alpha = 0.5f),
            disabledLabelColor = Color.LightGray,
            disabledTextColor = Color.White
        ),
        shape = RoundedCornerShape(12.dp),
        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    )
    // Add a transparent clickable overlay since 'enabled = false' disables clicks on the field itself
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .offset(y = (-56).dp)
            .background(Color.Transparent)
            .padding(1.dp)
            .clickable { onClick() }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, color = Color.LightGray) },
        modifier = Modifier.fillMaxWidth(),
        colors = TextFieldDefaults.outlinedTextFieldColors(
            focusedBorderColor = Color(0xFF7FDBFF),
            unfocusedBorderColor = Color.LightGray.copy(alpha = 0.5f),
            focusedLabelColor = Color(0xFF7FDBFF),
            cursorColor = Color(0xFF7FDBFF),
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White
        ),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        singleLine = singleLine,
        shape = RoundedCornerShape(12.dp)
    )
}
