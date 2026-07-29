package com.example.sentriai.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.WifiTethering
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sentriai.R
import com.example.sentriai.data.ProfileStore

/**
 * Profile + emergency handler form.
 *
 * @param onProfileSaved invoked once the form validates and the values are persisted.
 */
@Composable
fun ProfileSettingsScreen(onProfileSaved: () -> Unit = {}) {
    val context = LocalContext.current
    val sharedPreferences = remember { ProfileStore.preferences(context) }

    // Explicitly load colors from colors.xml
    val backgroundLight = colorResource(id = R.color.background_light)
    val darkNavy = colorResource(id = R.color.dark_navy)
    val blueIcon = colorResource(id = R.color.blue_icon)
    val purpleGlow = colorResource(id = R.color.purple_glow)
    val primaryText = colorResource(id = R.color.primary_text)
    val mutedText = colorResource(id = R.color.muted_text)
    val dividerColor = colorResource(id = R.color.divider)

    // Explicitly load the font family configured in google_sans.xml
    val GoogleSansFontFamily = remember { FontFamily(Font(R.font.google_sans)) }

    // Local state variables initialized from SharedPreferences
    var fullName by remember {
        mutableStateOf(sharedPreferences.getString(ProfileStore.KEY_FULL_NAME, "") ?: "")
    }
    var mobileNumber by remember {
        mutableStateOf(sharedPreferences.getString(ProfileStore.KEY_MOBILE_NUMBER, "") ?: "")
    }
    var handlerName by remember {
        mutableStateOf(sharedPreferences.getString(ProfileStore.KEY_HANDLER_NAME, "") ?: "")
    }
    var handlerMobile by remember {
        mutableStateOf(sharedPreferences.getString(ProfileStore.KEY_HANDLER_MOBILE, "") ?: "")
    }

    // Error states for validation
    var fullNameError by remember { mutableStateOf<String?>(null) }
    var mobileNumberError by remember { mutableStateOf<String?>(null) }
    var handlerNameError by remember { mutableStateOf<String?>(null) }
    var handlerMobileError by remember { mutableStateOf<String?>(null) }

    // Validation functions
    fun validateName(name: String, label: String): String? {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return "$label cannot be empty"
        if (trimmed.length < 2) return "$label must be at least 2 characters"
        if (!trimmed.all { it.isLetter() || it.isWhitespace() || it == '.' || it == '-' || it == '\'' }) {
            return "$label can only contain letters, spaces, dots, hyphens, or apostrophes"
        }
        return null
    }

    fun validatePhone(phone: String, label: String): String? {
        val trimmed = phone.trim()
        if (trimmed.isEmpty()) return "$label cannot be empty"
        val phoneRegex = "^\\+?[0-9\\s\\-\\(\\)]{7,20}$".toRegex()
        if (!phoneRegex.matches(trimmed)) return "Invalid $label format"
        return null
    }

    Scaffold(
        modifier = Modifier.safeDrawingPadding(), // Safe area view padding
        topBar = {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White)
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Shield,
                        contentDescription = "Shield Icon",
                        tint = Color.Black,
                        modifier = Modifier.size(26.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Profile Settings",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontFamily = GoogleSansFontFamily
                        ),
                        color = Color.Black
                    )
                }
                HorizontalDivider(color = dividerColor.copy(alpha = 0.3f), thickness = 1.dp)
            }
        },
        containerColor = backgroundLight
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Screen Header Section
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 28.dp, bottom = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Profile Settings",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 28.sp,
                        fontFamily = GoogleSansFontFamily
                    ),
                    color = darkNavy
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Update your security information and emergency contacts.",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = GoogleSansFontFamily
                    ),
                    color = mutedText,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp)
                )
            }

            // --- Section 1: YOUR INFO ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = "Your Info",
                    tint = blueIcon,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "YOUR INFO",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                        fontFamily = GoogleSansFontFamily
                    ),
                    color = blueIcon
                )
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFF1F4FA).copy(alpha = 0.6f)
                ),
                border = BorderStroke(1.dp, Color(0xFFE3E8F3))
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    // Full Name
                    Text(
                        text = "Full Name",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontFamily = GoogleSansFontFamily
                        ),
                        color = darkNavy,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    OutlinedTextField(
                        value = fullName,
                        onValueChange = {
                            fullName = it
                            fullNameError = if (fullNameError != null) validateName(it, "Full Name") else null
                        },
                        isError = fullNameError != null,
                        supportingText = {
                            if (fullNameError != null) {
                                Text(
                                    text = fullNameError!!,
                                    color = MaterialTheme.colorScheme.error,
                                    fontFamily = GoogleSansFontFamily
                                )
                            }
                        },
                        placeholder = {
                            Text(
                                text = "e.g. Alex Rivera",
                                color = mutedText,
                                fontFamily = GoogleSansFontFamily
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedContainerColor = Color.White,
                            focusedContainerColor = Color.White,
                            unfocusedBorderColor = Color(0xFFD5DBE6),
                            focusedBorderColor = purpleGlow
                        ),
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            fontFamily = GoogleSansFontFamily
                        ),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    // Mobile Number
                    Text(
                        text = "Mobile Number",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontFamily = GoogleSansFontFamily
                        ),
                        color = darkNavy,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    OutlinedTextField(
                        value = mobileNumber,
                        onValueChange = {
                            mobileNumber = it
                            mobileNumberError = if (mobileNumberError != null) validatePhone(it, "Mobile Number") else null
                        },
                        isError = mobileNumberError != null,
                        supportingText = {
                            if (mobileNumberError != null) {
                                Text(
                                    text = mobileNumberError!!,
                                    color = MaterialTheme.colorScheme.error,
                                    fontFamily = GoogleSansFontFamily
                                )
                            }
                        },
                        placeholder = {
                            Text(
                                text = "+1 (555) 000-0000",
                                color = mutedText,
                                fontFamily = GoogleSansFontFamily
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedContainerColor = Color.White,
                            focusedContainerColor = Color.White,
                            unfocusedBorderColor = Color(0xFFD5DBE6),
                            focusedBorderColor = purpleGlow
                        ),
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            fontFamily = GoogleSansFontFamily
                        ),
                        singleLine = true
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // --- Section 2: EMERGENCY HANDLER ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.WifiTethering,
                    contentDescription = "Emergency Handler",
                    tint = Color(0xFFC62828),
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "EMERGENCY HANDLER",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                        fontFamily = GoogleSansFontFamily
                    ),
                    color = Color(0xFFC62828)
                )
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White
                ),
                border = BorderStroke(1.dp, blueIcon)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "This person will be notified immediately if Guardian AI detects an urgent safety threat.",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontStyle = FontStyle.Normal, // Normal style per user's edit
                            lineHeight = 20.sp,
                            fontFamily = GoogleSansFontFamily
                        ),
                        color = primaryText.copy(alpha = 0.8f),
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    // Handler's Name
                    Text(
                        text = "Handler's Name",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontFamily = GoogleSansFontFamily
                        ),
                        color = darkNavy,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    OutlinedTextField(
                        value = handlerName,
                        onValueChange = {
                            handlerName = it
                            handlerNameError = if (handlerNameError != null) validateName(it, "Handler's Name") else null
                        },
                        isError = handlerNameError != null,
                        supportingText = {
                            if (handlerNameError != null) {
                                Text(
                                    text = handlerNameError!!,
                                    color = MaterialTheme.colorScheme.error,
                                    fontFamily = GoogleSansFontFamily
                                )
                            }
                        },
                        placeholder = {
                            Text(
                                text = "e.g. Jordan Smith",
                                color = mutedText,
                                fontFamily = GoogleSansFontFamily
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedContainerColor = Color.White,
                            focusedContainerColor = Color.White,
                            unfocusedBorderColor = Color(0xFFD5DBE6),
                            focusedBorderColor = purpleGlow
                        ),
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            fontFamily = GoogleSansFontFamily
                        ),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    // Handler's Mobile
                    Text(
                        text = "Handler's Mobile",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontFamily = GoogleSansFontFamily
                        ),
                        color = darkNavy,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    OutlinedTextField(
                        value = handlerMobile,
                        onValueChange = {
                            handlerMobile = it
                            handlerMobileError = if (handlerMobileError != null) validatePhone(it, "Handler's Mobile") else null
                        },
                        isError = handlerMobileError != null,
                        supportingText = {
                            if (handlerMobileError != null) {
                                Text(
                                    text = handlerMobileError!!,
                                    color = MaterialTheme.colorScheme.error,
                                    fontFamily = GoogleSansFontFamily
                                )
                            }
                        },
                        placeholder = {
                            Text(
                                text = "+1 (555) 999-9999",
                                color = mutedText,
                                fontFamily = GoogleSansFontFamily
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedContainerColor = Color.White,
                            focusedContainerColor = Color.White,
                            unfocusedBorderColor = Color(0xFFD5DBE6),
                            focusedBorderColor = purpleGlow
                        ),
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            fontFamily = GoogleSansFontFamily
                        ),
                        singleLine = true
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // --- Save Profile Button ---
            Button(
                onClick = {
                    // Trigger validation on all fields
                    val nameErr = validateName(fullName, "Full Name")
                    val phoneErr = validatePhone(mobileNumber, "Mobile Number")
                    val hNameErr = validateName(handlerName, "Handler's Name")
                    val hPhoneErr = validatePhone(handlerMobile, "Handler's Mobile")

                    fullNameError = nameErr
                    mobileNumberError = phoneErr
                    handlerNameError = hNameErr
                    handlerMobileError = hPhoneErr

                    // Save details only if all validations pass
                    if (nameErr == null && phoneErr == null && hNameErr == null && hPhoneErr == null) {
                        sharedPreferences.edit().apply {
                            putString(ProfileStore.KEY_FULL_NAME, fullName.trim())
                            putString(ProfileStore.KEY_MOBILE_NUMBER, mobileNumber.trim())
                            putString(ProfileStore.KEY_HANDLER_NAME, handlerName.trim())
                            putString(ProfileStore.KEY_HANDLER_MOBILE, handlerMobile.trim())
                            apply()
                        }
                        Toast.makeText(context, "Profile Saved Successfully!", Toast.LENGTH_SHORT).show()
                        onProfileSaved()
                    } else {
                        Toast.makeText(context, "Please correct the errors in the form.", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier
                    .width(200.dp)
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = darkNavy
                ),
                elevation = ButtonDefaults.buttonElevation(
                    defaultElevation = 8.dp,
                    pressedElevation = 14.dp,
                    disabledElevation = 0.dp
                )
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Save Profile",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            fontFamily = GoogleSansFontFamily
                        ),
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Icon(
                        imageVector = Icons.Default.Save,
                        contentDescription = "Save Icon",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
