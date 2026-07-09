package com.cashguard.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cashguard.app.ui.i18n.LocalStrings
import com.cashguard.app.ui.i18n.SUPPORTED_LANGUAGES
import com.cashguard.app.ui.theme.*

/**
 * First-launch flow: pick a language (the rest of the flow re-renders in it
 * immediately), then a short intro with the privacy promise front and centre.
 */
@Composable
fun OnboardingScreen(
    selectedLanguage: String,
    onLanguageSelected: (String) -> Unit,
    onDone: () -> Unit
) {
    var step by remember { mutableIntStateOf(0) }
    val s = LocalStrings.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
            .verticalScroll(rememberScrollState())
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(40.dp))
        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(Brush.linearGradient(listOf(PrimaryPurple, AccentBlue))),
            contentAlignment = Alignment.Center
        ) {
            Text("💰", fontSize = 40.sp)
        }
        Spacer(Modifier.height(18.dp))
        Text("CashGuard", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 30.sp)

        if (step == 0) {
            Spacer(Modifier.height(36.dp))
            Text(
                s.onboardChooseLanguage,
                color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 18.sp
            )
            Spacer(Modifier.height(20.dp))
            SUPPORTED_LANGUAGES.forEach { lang ->
                val selected = lang.code == selectedLanguage
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(if (selected) PrimaryPurple else SurfaceDark)
                        .clickable { onLanguageSelected(lang.code) }
                        .padding(horizontal = 20.dp, vertical = 18.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            lang.nativeName,
                            color = if (selected) Color.White else TextPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 17.sp
                        )
                        if (lang.englishName != lang.nativeName) {
                            Text(
                                lang.englishName,
                                color = if (selected) Color.White.copy(alpha = 0.8f) else TextSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }
                    RadioButton(
                        selected = selected,
                        onClick = { onLanguageSelected(lang.code) },
                        colors = RadioButtonDefaults.colors(
                            selectedColor = Color.White,
                            unselectedColor = TextSecondary
                        )
                    )
                }
            }
            Spacer(Modifier.height(28.dp))
            Button(
                onClick = { step = 1 },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple)
            ) {
                Text("➜", fontSize = 18.sp)
            }
        } else {
            Spacer(Modifier.height(8.dp))
            Text(
                s.onboardTagline,
                color = TextSecondary, fontSize = 14.sp, textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(26.dp))

            FeatureRow(Icons.Filled.AutoAwesome, s.onboardFeatAutoTitle, s.onboardFeatAutoDesc)
            FeatureRow(Icons.Filled.Checklist, s.onboardFeatBudgetTitle, s.onboardFeatBudgetDesc)
            FeatureRow(Icons.Filled.Celebration, s.onboardFeatPartyTitle, s.onboardFeatPartyDesc)

            Spacer(Modifier.height(20.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(SuccessGreen.copy(alpha = 0.12f))
                    .padding(18.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Lock, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        s.onboardPrivacyTitle,
                        color = SuccessGreen, fontWeight = FontWeight.Bold, fontSize = 15.sp
                    )
                }
                Spacer(Modifier.height(10.dp))
                PrivacyBullet(s.onboardPrivacy1)
                PrivacyBullet(s.onboardPrivacy2)
                PrivacyBullet(s.onboardPrivacy3)
            }

            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Code, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text(s.onboardOpenSource + " • MIT", color = TextSecondary, fontSize = 12.sp)
            }

            Spacer(Modifier.height(24.dp))
            Button(
                onClick = onDone,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple)
            ) {
                Text(s.getStarted, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = Color.White)
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun FeatureRow(icon: ImageVector, title: String, desc: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(PrimaryPurple.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = PrimaryPurpleLight, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Text(desc, color = TextSecondary, fontSize = 13.sp)
        }
    }
}

@Composable
private fun PrivacyBullet(text: String) {
    Row(modifier = Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.Top) {
        Text("✓", color = SuccessGreen, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(8.dp))
        Text(text, color = TextPrimary, fontSize = 13.sp)
    }
}
