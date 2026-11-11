package com.medocmaman.ui.screens

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.medocmaman.alarm.AlarmReceiver
import com.medocmaman.alarm.MedicationAlarmScheduler
import com.medocmaman.alerts.ActiveCaregiverAlert
import com.medocmaman.alerts.CaregiverSirenController
import com.medocmaman.data.local.AppPreferences
import com.medocmaman.data.local.AppRole
import com.medocmaman.data.remote.FirebaseRepository
import com.medocmaman.ui.theme.AmberOrange
import com.medocmaman.ui.theme.CrimsonRed
import com.medocmaman.ui.theme.DarkText
import com.medocmaman.ui.theme.EmeraldGreen
import com.medocmaman.ui.theme.RoyalBlue
import com.medocmaman.ui.theme.SlateGrayButton
import com.medocmaman.ui.theme.WarmOffWhite

/**
 * Phase 1: Xiaomi System Health & Permissions Screen + Family Configuration + 14-Day Test Harness.
 * Accessible by holding the ⚙️ icon on Screen 5 for 3 seconds.
 */
@Composable
fun AdminSetupScreen(
    prefs: AppPreferences,
    onRequestRuntimePermissions: () -> Unit,
    onOpenMeuhedetTest: () -> Unit,
    onCloseAdmin: () -> Unit
) {
    val context = LocalContext.current
    var motherName by remember { mutableStateOf(prefs.motherFirstName) }
    var motherAddress by remember { mutableStateOf(prefs.motherAddress) }
    var sonPhone by remember { mutableStateOf(prefs.sonPhoneNumber) }
    var daughterPhone by remember { mutableStateOf(prefs.daughterPhoneNumber) }
    var caregiverName by remember { mutableStateOf(prefs.caregiverName) }
    var useVouvoiement by remember { mutableStateOf(prefs.useVouvoiement) }
    var selectedRole by remember { mutableStateOf(prefs.appRole) }

    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager

    val canExactAlarm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        alarmManager.canScheduleExactAlarms()
    } else true

    val canFullScreen = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        notificationManager.canUseFullScreenIntent()
    } else true

    val ignoresBatteryOpt = powerManager.isIgnoringBatteryOptimizations(context.packageName)

    val callChannel = notificationManager.getNotificationChannel(AlarmReceiver.INCOMING_CALL_CHANNEL_ID)
    val channelHighOk = callChannel == null || callChannel.importance >= NotificationManager.IMPORTANCE_HIGH

    val hasMicPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    val hasSmsPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED
    val isFirebaseLive = FirebaseRepository.isFirebaseConfigured(context)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(WarmOffWhite)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "⚙️ Diagnostic Xiaomi & Config Famille",
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                color = DarkText,
                modifier = Modifier.weight(1f)
            )
            Button(
                onClick = onCloseAdmin,
                colors = ButtonDefaults.buttonColors(containerColor = SlateGrayButton)
            ) {
                Text("Fermer")
            }
        }

        // 1. Test Harness for the 14-Day Xiaomi Feasibility Protocol
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(2.dp, EmeraldGreen)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "🧪 Tests d'Appel & Réveil Écran (Protocole 14 Jours)",
                    fontSize = 19.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = DarkText
                )

                Button(
                    onClick = {
                        MedicationAlarmScheduler.scheduleTestCall(context, delaySeconds = 1)
                        Toast.makeText(context, "Appel test lancé !", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("🔔 Déclencher un appel test MAINTENANT", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = {
                        MedicationAlarmScheduler.scheduleTestCall(context, delaySeconds = 10)
                        Toast.makeText(
                            context,
                            "Appel programmé dans 10s — Verrouillez l'écran maintenant !",
                            Toast.LENGTH_LONG
                        ).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RoyalBlue),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("⏱️ Appel test dans 10s (Verrouillez l'écran !)", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = onOpenMeuhedetTest,
                    colors = ButtonDefaults.buttonColors(containerColor = AmberOrange),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("🏥 Tester l'écran Renouvellement Meuhedet", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = {
                        CaregiverSirenController.startSiren(
                            context,
                            ActiveCaregiverAlert(
                                callLogId = "test_siren_${System.currentTimeMillis()}",
                                title = "Test Sirène Aidant",
                                message = "Maman n'a pas répondu après 3 essais (Test)",
                                alertLevel = "IMMEDIATE"
                            )
                        )
                        Toast.makeText(context, "Sirène lancée (basculez en Mode Aidant pour l'arrêter)", Toast.LENGTH_LONG).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonRed),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("🚨 Tester la Sirène Aidant", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // 2. Xiaomi System Health & Permissions Checklist
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(2.dp, RoyalBlue)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "🛡️ Santé Système Xiaomi Redmi (HyperOS / MIUI)",
                    fontSize = 19.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = DarkText
                )

                StatusRow("Exact Alarms (setAlarmClock)", canExactAlarm)
                StatusRow("Full-Screen Intent (Écran verrouillé)", canFullScreen)
                StatusRow("Exemption Économiseur de batterie", ignoresBatteryOpt)
                StatusRow("Canal Notification Haute Priorité", channelHighOk)
                StatusRow("Permissions Micro & SMS", hasMicPerm && hasSmsPerm)
                StatusRow(
                    label = if (isFirebaseLive) "Firebase Firestore Connecté" else "Mode Local Offline (google-services.json placeholder)",
                    ok = true
                )

                Spacer(modifier = Modifier.height(6.dp))

                Button(
                    onClick = onRequestRuntimePermissions,
                    colors = ButtonDefaults.buttonColors(containerColor = RoyalBlue),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("1. Autoriser Micro, SMS, Notifications & Téléphone")
                }

                if (!ignoresBatteryOpt) {
                    Button(
                        onClick = {
                            val intent = Intent(
                                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                Uri.parse("package:${context.packageName}")
                            )
                            context.startActivity(intent)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AmberOrange),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("2. Désactiver l'optimisation de batterie")
                    }
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && !canFullScreen) {
                    Button(
                        onClick = {
                            val intent = Intent(
                                Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                                Uri.parse("package:${context.packageName}")
                            )
                            context.startActivity(intent)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AmberOrange),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("3. Autoriser l'affichage plein écran (Android 14+)")
                    }
                }

                Button(
                    onClick = { openXiaomiPermissionEditor(context) },
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonRed),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("📲 Ouvrir les Permissions Spéciales Xiaomi (MIUI / HyperOS)")
                }

                Text(
                    text = "Checklist manuelle Xiaomi Redmi :\n" +
                        "1. Démarrage automatique (Autostart) = ACTIVÉ\n" +
                        "2. Économiseur de batterie = Aucune restriction\n" +
                        "3. Afficher sur l'écran de verrouillage = ACTIVÉ\n" +
                        "4. Afficher des fenêtres pop-up en arrière-plan = ACTIVÉ\n" +
                        "5. Verrouiller l'application dans les Récents (cadenas) = ACTIVÉ",
                    fontSize = 15.sp,
                    color = Color(0xFF334155)
                )
            }
        }

        // 3. Role Switch & Family Configuration
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Color(0xFFCBD5E1))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "👨‍👩‍👧 Rôle du Téléphone & Contacts",
                    fontSize = 19.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = DarkText
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { selectedRole = AppRole.PATIENT },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (selectedRole == AppRole.PATIENT) EmeraldGreen else SlateGrayButton
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("👵 Téléphone Maman")
                    }
                    Button(
                        onClick = { selectedRole = AppRole.CAREGIVER },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (selectedRole == AppRole.CAREGIVER) RoyalBlue else SlateGrayButton
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("📱 Téléphone Enfant")
                    }
                }

                OutlinedTextField(
                    value = motherName,
                    onValueChange = { motherName = it },
                    label = { Text("Prénom de Maman") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = motherAddress,
                    onValueChange = { motherAddress = it },
                    label = { Text("Adresse complète de Maman (envoyée en cas d'urgence 101)") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = sonPhone,
                    onValueChange = { sonPhone = it },
                    label = { Text("Téléphone du Fils (SMS & Appel direct)") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = daughterPhone,
                    onValueChange = { daughterPhone = it },
                    label = { Text("Téléphone de la Fille (SMS & Appel direct)") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = caregiverName,
                    onValueChange = { caregiverName = it },
                    label = { Text("Votre prénom (pour 'Pris en charge par...' en mode Aidant)") },
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (useVouvoiement) "Ton vocal : Vouvoiement (\"Vous\")" else "Ton vocal : Tutoiement (\"Tu\")",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = DarkText
                    )
                    Switch(
                        checked = useVouvoiement,
                        onCheckedChange = { useVouvoiement = it }
                    )
                }

                Button(
                    onClick = {
                        prefs.motherFirstName = motherName
                        prefs.motherAddress = motherAddress
                        prefs.sonPhoneNumber = sonPhone
                        prefs.daughterPhoneNumber = daughterPhone
                        prefs.caregiverName = caregiverName
                        prefs.useVouvoiement = useVouvoiement
                        prefs.appRole = selectedRole
                        FirebaseRepository.syncTopicsForRole(context, selectedRole == AppRole.CAREGIVER)
                        Toast.makeText(context, "Configuration enregistrée !", Toast.LENGTH_SHORT).show()
                        onCloseAdmin()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("💾 Enregistrer et Fermer", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun StatusRow(label: String, ok: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, fontSize = 16.sp, color = DarkText, modifier = Modifier.weight(1f))
        Text(
            text = if (ok) "✅ OK" else "❌ À activer",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = if (ok) EmeraldGreen else CrimsonRed
        )
    }
}

private fun openXiaomiPermissionEditor(context: Context) {
    try {
        val miuiIntent = Intent("miui.intent.action.APP_PERM_EDITOR").apply {
            setClassName(
                "com.miui.securitycenter",
                "com.miui.permcenter.permissions.PermissionsEditorActivity"
            )
            putExtra("extra_pkgname", context.packageName)
        }
        context.startActivity(miuiIntent)
    } catch (_: Exception) {
        try {
            val fallbackMiui = Intent("miui.intent.action.APP_PERM_EDITOR").apply {
                putExtra("extra_pkgname", context.packageName)
            }
            context.startActivity(fallbackMiui)
        } catch (_: Exception) {
            val appSettings = Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:${context.packageName}")
            )
            context.startActivity(appSettings)
        }
    }
}
