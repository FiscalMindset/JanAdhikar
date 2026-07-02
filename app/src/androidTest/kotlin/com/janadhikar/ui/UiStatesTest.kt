package com.janadhikar.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.janadhikar.engine.AgentPhase
import com.janadhikar.engine.IncidentState
import com.janadhikar.input.AppLanguage
import com.janadhikar.llm.Directive
import com.janadhikar.memory.model.VerifiedCitation
import com.janadhikar.sos.SosController
import com.janadhikar.ui.theme.JanadhikarTheme
import kotlinx.coroutines.MainScope
import org.junit.Rule
import org.junit.Test

/** Compose tests for the three UI states (CONTRIBUTING.md ui/ test rule). */
class UiStatesTest {

    @get:Rule
    val compose = createComposeRule()

    private val citation = VerifiedCitation(
        chunkId = 1L,
        statuteName = "Bharatiya Nagarik Suraksha Sanhita, 2023",
        statuteNameHi = "भारतीय नागरिक सुरक्षा संहिता, 2023",
        sectionNumber = "47",
        clause = "(1)",
        pageNumber = 21,
        verbatimTextEn = "Grounds of arrest shall be communicated forthwith.",
        verbatimTextHi = "गिरफ्तारी के आधार तुरंत बताए जाएँगे।",
        sourceDocument = "BNSS_2023_Gazette.pdf",
        compilationDate = "2026-05-01",
    )

    private fun neverConfiguredSos() = SosController(
        scope = MainScope(),
        dispatcher = object : SosController.Dispatcher {
            override fun isConfigured() = false
            override fun dispatch() = false
        },
    )

    // ── State 1: Trigger ─────────────────────────────────────────────────────

    @Test
    fun trigger_showsMicAndTextPath_whenReady() {
        var voiceStarted = false
        compose.setContent {
            JanadhikarTheme {
                TriggerScreen(
                    engineReady = true,
                    onStartVoice = { voiceStarted = true },
                    onSubmitText = {},
                )
            }
        }
        compose.onNodeWithTag("mic_button").assertIsDisplayed().performClick()
        assert(voiceStarted)
        compose.onNodeWithTag("typed_query_field").assertIsDisplayed()
        compose.onNodeWithTag("submit_text_button").assertIsDisplayed()
    }

    @Test
    fun trigger_disablesSubmit_whileEngineWarmsUp() {
        compose.setContent {
            JanadhikarTheme {
                TriggerScreen(engineReady = false, onStartVoice = {}, onSubmitText = {})
            }
        }
        compose.onNodeWithTag("submit_text_button").assertIsNotEnabled()
    }

    // ── State 2: Active ──────────────────────────────────────────────────────

    @Test
    fun active_showsTranscriptTickerAndStop() {
        var stopped = false
        compose.setContent {
            JanadhikarTheme {
                ActiveScreen(
                    transcript = "police won't tell me why",
                    phase = AgentPhase.LISTENING,
                    elapsedMillis = 7_000L,
                    onStop = { stopped = true },
                )
            }
        }
        compose.onNodeWithText("police won't tell me why").assertIsDisplayed()
        compose.onNodeWithTag("agent_ticker").assertIsDisplayed()
        compose.onNodeWithTag("stop_button").performClick()
        assert(stopped)
    }

    // ── State 3: Resolution — Shield ─────────────────────────────────────────

    @Test
    fun shield_rendersDirectiveAndCitationFromTypedFieldsOnly() {
        val shield = IncidentState.Shield(
            directive = Directive("Demand the grounds of your arrest now.", AppLanguage.ENGLISH, false),
            citation = citation,
            related = emptyList(),
            confidence = 0.9f,
            redirectedFromSuperseded = false,
        )
        compose.setContent {
            JanadhikarTheme { ShieldScreen(shield = shield, sos = neverConfiguredSos(), onDone = {}) }
        }
        compose.onNodeWithTag("directive_text").assertIsDisplayed()
        compose.onNodeWithText("Demand the grounds of your arrest now.").assertIsDisplayed()
        // Receipt values — each a typed DB field
        compose.onNodeWithText("47").assertIsDisplayed()
        compose.onNodeWithText("21").assertIsDisplayed()
        compose.onNodeWithText("Bharatiya Nagarik Suraksha Sanhita, 2023").assertIsDisplayed()
    }

    @Test
    fun shield_rendersHindiStatuteName_whenDirectiveIsHindi() {
        val shield = IncidentState.Shield(
            directive = Directive("अभी गिरफ्तारी का कारण पूछें।", AppLanguage.HINDI, false),
            citation = citation,
            related = emptyList(),
            confidence = 0.9f,
            redirectedFromSuperseded = false,
        )
        compose.setContent {
            JanadhikarTheme { ShieldScreen(shield = shield, sos = neverConfiguredSos(), onDone = {}) }
        }
        compose.onNodeWithText("अभी गिरफ्तारी का कारण पूछें।").assertIsDisplayed()
        compose.onNodeWithText("भारतीय नागरिक सुरक्षा संहिता, 2023").assertIsDisplayed()
    }

    // ── State 3: Resolution — the governed refusal ───────────────────────────

    @Test
    fun refusal_rendersTheExactGovernedString_andNothingElse() {
        compose.setContent {
            JanadhikarTheme { NoStatuteScreen(sos = neverConfiguredSos(), onDone = {}) }
        }
        compose.onNodeWithTag("refusal_text").assertIsDisplayed()
        compose.onNodeWithText("No verified legal statute found. Do not speculate.")
            .assertIsDisplayed()
        compose.onNodeWithTag("citation_card").assertDoesNotExist()
    }
}
