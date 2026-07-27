package com.example.sentriai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.appcompat.app.AppCompatDelegate
import com.example.sentriai.ui.theme.SentriAITheme
import com.example.sentriai.ui.theme.DarkNavy
import com.example.sentriai.ui.theme.BlueIcon
import com.example.sentriai.ui.theme.PurpleGlow
import com.example.sentriai.ui.theme.PrimaryText
import com.example.sentriai.ui.theme.MutedText

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        super.onCreate(savedInstanceState)
        setContent {
            SentriAITheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SentriAIScreen()
                }
            }
        }
    }
}

@Composable
fun SentriAIScreen() {
    var isPressed by remember { mutableStateOf(false) }
    var interactionCount by remember { mutableStateOf(0) }
    
    val buttonScale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1.0f,
        label = "ButtonScale"
    )
    
    val buttonColor by animateColorAsState(
        targetValue = if (isPressed) PurpleGlow else DarkNavy,
        label = "ButtonColor"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top Brand Header
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 40.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .shadow(elevation = 12.dp, shape = RoundedCornerShape(24.dp))
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(DarkNavy, PurpleGlow)
                        ),
                        shape = RoundedCornerShape(24.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Sentri AI Logo",
                    tint = Color.White,
                    modifier = Modifier.size(36.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                text = "SENTRI AI",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "Security & Intelligence Platform",
                style = MaterialTheme.typography.bodyMedium,
                color = MutedText,
                fontWeight = FontWeight.Medium
            )
        }

        // Center Premium Info Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp)
                .shadow(
                    elevation = 6.dp, 
                    shape = RoundedCornerShape(20.dp),
                    ambientColor = PurpleGlow,
                    spotColor = PurpleGlow
                ),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            border = BorderStroke(1.dp, PurpleGlow.copy(alpha = 0.2f))
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = "Project Configured Successfully",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                HorizontalDivider(color = MutedText.copy(alpha = 0.3f), thickness = 1.dp)
                
                Spacer(modifier = Modifier.height(16.dp))

                // Feature list with Blue Icons
                FeatureRow(
                    icon = Icons.Default.CheckCircle,
                    title = "Font System",
                    subtitle = "Google Sans integration complete"
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                FeatureRow(
                    icon = Icons.Default.Settings,
                    title = "Color Palette",
                    subtitle = "F7F8FC (bg), 171F3A (navy), 6B5CFF (glow)"
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                FeatureRow(
                    icon = Icons.Default.Info,
                    title = "UI Theme",
                    subtitle = "Light/Dark modes mapped dynamically"
                )
            }
        }

        // Bottom CTA Buttons & Status
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Interactions: $interactionCount",
                style = MaterialTheme.typography.bodySmall,
                color = MutedText,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Button(
                onClick = {
                    interactionCount++
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .scale(buttonScale),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = buttonColor
                ),
                border = BorderStroke(2.dp, PurpleGlow),
                elevation = ButtonDefaults.buttonElevation(
                    defaultElevation = 8.dp,
                    pressedElevation = 2.dp
                )
            ) {
                Text(
                    text = "Get Started",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
            
            // Simulating button press transitions for micro-animations
            LaunchedEffect(interactionCount) {
                if (interactionCount > 0) {
                    isPressed = true
                    kotlinx.coroutines.delay(100)
                    isPressed = false
                }
            }
        }
    }
}

@Composable
fun FeatureRow(icon: ImageVector, title: String, subtitle: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(BlueIcon.copy(alpha = 0.12f), shape = RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = BlueIcon,
                modifier = Modifier.size(20.dp)
            )
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MutedText
            )
        }
    }
}
