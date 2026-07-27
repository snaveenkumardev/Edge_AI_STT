package com.example.safetymode.ui.screens

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.safetymode.R
import com.example.safetymode.ui.theme.SafetyModeTheme

/** Palette for the Guardian AI activation surface — intentionally light-only, as designed. */
private val PageBackground = Color(0xFFF2F6FD)
private val TopBarBackground = Color(0xFFFFFFFF)
private val CardBackground = Color(0xFFFFFFFF)
private val NavyInk = Color(0xFF111A32)
private val AccentBlue = Color(0xFF2563EB)
private val SoftBlueContainer = Color(0xFFE9F0FE)
private val MutedText = Color(0xFF64748B)
private val HaloRing = Color(0xFFE4EBF8)
private val CardBorder = Color(0xFFEDF1F9)

/** Power button states: slate grey while off, green once armed. */
private val PowerOffGrey = Color(0xFF64748B)
private val PowerOnGreen = Color(0xFF15803D)

/**
 * Guardian AI activation screen: assistant portrait, a single power toggle and the
 * live monitoring status tiles.
 *
 * @param isActive whether the assistant is currently armed.
 * @param onPowerClick invoked when the power pill is tapped.
 * @param onProfileClick invoked when the profile chip in the top bar is tapped.
 */
@Composable
fun AiAssistantActivateScreen(
    onProfileClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isActive by remember { mutableStateOf(true) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(PageBackground),
    ) {
        GuardianTopBar(onProfileClick = onProfileClick)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(24.dp))

            AssistantPortrait()

            Spacer(Modifier.height(28.dp))

            Text(
                text = stringResource(
                    if (isActive) R.string.ai_assistant_status_active
                    else R.string.ai_assistant_status_ready,
                ),
                color = NavyInk,
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(10.dp))

            Text(
                text = stringResource(
                    if (isActive) R.string.ai_assistant_status_active_detail
                    else R.string.ai_assistant_status_ready_detail,
                ),
                color = MutedText,
                fontSize = 15.sp,
                lineHeight = 22.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 12.dp),
            )

            Spacer(Modifier.height(28.dp))

            PowerButton(isActive = isActive, onClick = { isActive = !isActive})

            Spacer(Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.ai_assistant_protocol).uppercase(),
                color = MutedText,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.4.sp,
            )

            Spacer(Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                StatusTile(
                    icon = R.drawable.ic_location_pin,
                    label = R.string.ai_assistant_location_label,
                    value = R.string.ai_assistant_location_value,
                    modifier = Modifier.weight(1f),
                )
                StatusTile(
                    icon = R.drawable.ic_shield,
                    label = R.string.ai_assistant_vitals_label,
                    value = R.string.ai_assistant_vitals_value,
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun GuardianTopBar(onProfileClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(TopBarBackground)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_shield),
            contentDescription = null,
            tint = NavyInk,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.size(8.dp))
        Text(
            text = stringResource(R.string.ai_assistant_app_title),
            color = NavyInk,
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.weight(1f))
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(SoftBlueContainer)
                .clickable(onClick = onProfileClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Person,
                contentDescription = stringResource(R.string.ai_assistant_profile),
                tint = Color(0xFF4A6FA5),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/** Assistant avatar framed by concentric halo rings, with a verified shield badge. */
@Composable
private fun AssistantPortrait() {
    Box(
        modifier = Modifier.size(268.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = size.minDimension / 2f
            listOf(0.99f, 0.86f).forEach { fraction ->
                drawCircle(
                    color = HaloRing,
                    radius = center * fraction,
                    style = Stroke(width = 1.dp.toPx()),
                )
            }
        }

        Image(
            painter = painterResource(R.drawable.ai_assistant_img),
            contentDescription = stringResource(R.string.ai_assistant_avatar),
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(206.dp)
                .shadow(elevation = 14.dp, shape = CircleShape, clip = false)
                .clip(CircleShape)
                .background(Color.White)
                .border(width = 5.dp, color = Color.White, shape = CircleShape),
        )

        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 22.dp, end = 18.dp)
                .size(34.dp)
                .shadow(elevation = 6.dp, shape = CircleShape)
                .clip(CircleShape)
                .background(CardBackground),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_shield),
                contentDescription = stringResource(R.string.ai_assistant_verified_badge),
                tint = AccentBlue,
                modifier = Modifier.size(17.dp),
            )
        }
    }
}

/**
 * Full-width power button. Grey while the assistant is off, green once it is on;
 * the label and trailing knob track the same state.
 */
@Composable
private fun PowerButton(isActive: Boolean, onClick: () -> Unit) {
    val containerColor = if (isActive) PowerOnGreen else PowerOffGrey
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(74.dp),
        shape = RoundedCornerShape(37.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = Color.White,
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
        contentPadding = PaddingValues(start = 28.dp, end = 11.dp),
    ) {
        Text(
            text = stringResource(
                if (isActive) R.string.ai_assistant_power_on
                else R.string.ai_assistant_power_off,
            ),
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.3.sp,
        )
        Spacer(Modifier.weight(1f))
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(Color.White),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_power),
                // Pairs with the state label above so TalkBack announces state + action.
                contentDescription = stringResource(
                    if (isActive) R.string.ai_assistant_deactivate
                    else R.string.ai_assistant_activate,
                ),
                tint = containerColor,
                modifier = Modifier.size(25.dp),
            )
        }
    }
}

@Composable
private fun StatusTile(
    @DrawableRes icon: Int,
    @StringRes label: Int,
    @StringRes value: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(CardBackground)
            .border(width = 1.dp, color = CardBorder, shape = RoundedCornerShape(18.dp))
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(SoftBlueContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = AccentBlue,
                modifier = Modifier.size(17.dp),
            )
        }
        Spacer(Modifier.size(10.dp))
        Column {
            Text(
                text = stringResource(label).uppercase(),
                color = MutedText,
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.9.sp,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = stringResource(value),
                color = NavyInk,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 335, heightDp = 690)
@Composable
private fun AiAssistantActivateScreenPreview() {
    SafetyModeTheme(dynamicColor = false) {
        AiAssistantActivateScreen(
            onProfileClick = {},
        )
    }
}
