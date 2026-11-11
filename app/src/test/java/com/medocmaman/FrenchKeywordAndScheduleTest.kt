package com.medocmaman

import com.medocmaman.alarm.DailyCheckScheduler
import com.medocmaman.data.local.MedicalExamEntity
import com.medocmaman.data.local.MedicalExamState
import com.medocmaman.data.local.MedicationEntity
import com.medocmaman.data.local.PrescriptionStatus
import com.medocmaman.voice.FrenchKeywordMatcher
import com.medocmaman.voice.FrenchVoiceHelper
import com.medocmaman.voice.VoiceIntent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale

class FrenchKeywordAndScheduleTest {

    @Test
    fun `VITE emergency keywords are prioritized over everything`() {
        val emergencyPhrases = listOf(
            "J'ai des vertiges depuis ce matin",
            "Ma tête tourne",
            "Je suis tombée dans le salon",
            "J'ai une faiblesse dans le bras gauche",
            "J'ai du mal à parler",
            "Problème de vue soudain",
            "Oui mais j'ai très mal à la poitrine"
        )
        for (phrase in emergencyPhrases) {
            assertEquals(
                "Expected EMERGENCY_VITE for phrase: $phrase",
                VoiceIntent.EMERGENCY_VITE,
                FrenchKeywordMatcher.classifyTranscript(phrase)
            )
        }
    }

    @Test
    fun `Negative and delay phrases classify as NEGATIVE_OR_DELAY`() {
        val negativePhrases = listOf(
            "Non",
            "Non, pas encore pris",
            "Pas encore",
            "J'ai oublié tout à l'heure",
            "Rappelle-moi plus tard"
        )
        for (phrase in negativePhrases) {
            assertEquals(
                "Expected NEGATIVE_OR_DELAY for phrase: $phrase",
                VoiceIntent.NEGATIVE_OR_DELAY,
                FrenchKeywordMatcher.classifyTranscript(phrase)
            )
        }
    }

    @Test
    fun `Affirmative phrases classify as AFFIRMATIVE_TAKEN`() {
        val affirmativePhrases = listOf(
            "Oui",
            "Oui je l'ai pris",
            "C'est fait merci",
            "Déjà pris"
        )
        for (phrase in affirmativePhrases) {
            assertEquals(
                "Expected AFFIRMATIVE_TAKEN for phrase: $phrase",
                VoiceIntent.AFFIRMATIVE_TAKEN,
                FrenchKeywordMatcher.classifyTranscript(phrase)
            )
        }
    }

    @Test
    fun `Meuhedet 14-day renewal window triggers only when within 14 days and not yet opened`() {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val refTime = sdf.parse("2026-12-03")!!.time // 12 days before 2026-12-15

        val medActive = MedicationEntity(
            id = "med_statin",
            nameFr = "Atorvastatine",
            dosageFr = "Un comprimé",
            mealTimingFr = "après le petit-déjeuner",
            visualDescriptionFr = "petit comprimé blanc",
            scheduledTimesCsv = "08:00",
            onMissedInstructionFr = "Ne pas doubler",
            prescriptionExpiryDate = "2026-12-15",
            stockEmptyDate = "2026-12-20",
            prescriptionStatus = PrescriptionStatus.ACTIVE.name
        )
        assertTrue(DailyCheckScheduler.isRenewalDueWithin14Days(medActive, refTime))

        val medAlreadyOpened = medActive.copy(
            prescriptionStatus = PrescriptionStatus.REQUEST_OPENED_MEUHEDET.name
        )
        assertFalse(DailyCheckScheduler.isRenewalDueWithin14Days(medAlreadyOpened, refTime))
    }

    @Test
    fun `Medical exam tracker flags NEEDS_REFERRAL within 30 days`() {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val refTime = sdf.parse("2026-12-25")!!.time // 21 days before 2027-01-15

        val carotidExam = MedicalExamEntity(
            id = "exam_carotid_duplex",
            nameFr = "Échographie-Doppler des carotides",
            nameHe = "אולטרסאונד דופלקס של עורקי הצוואר",
            state = MedicalExamState.NEEDS_REFERRAL.name,
            lastExamDate = "2026-01-15",
            nextTargetDate = "2027-01-15",
            stateUpdatedAt = "2026-11-01"
        )
        assertTrue(DailyCheckScheduler.isExamActionRequired(carotidExam, refTime))

        val bookedExam = carotidExam.copy(state = MedicalExamState.APPOINTMENT_BOOKED.name)
        assertFalse(DailyCheckScheduler.isExamActionRequired(bookedExam, refTime))
    }

    @Test
    fun `FrenchVoiceHelper supports both Vouvoiement and Tutoiement`() {
        val vouvoiementGreeting = FrenchVoiceHelper.buildGreetingScript(
            motherName = "Rachel",
            medNameFr = "Atorvastatine",
            visualDescriptionFr = "le petit comprimé rond et blanc",
            dosageFr = "Un comprimé",
            mealTimingFr = "après le petit-déjeuner",
            vouvoiement = true
        )
        assertTrue(vouvoiementGreeting.contains("L'avez-vous déjà pris ?"))

        val tutoiementGreeting = FrenchVoiceHelper.buildGreetingScript(
            motherName = "Rachel",
            medNameFr = "Atorvastatine",
            visualDescriptionFr = "le petit comprimé rond et blanc",
            dosageFr = "Un comprimé",
            mealTimingFr = "après le petit-déjeuner",
            vouvoiement = false
        )
        assertTrue(tutoiementGreeting.contains("L'as-tu déjà pris ?"))
    }
}
