package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.audio.BatchEngine
import com.example.audio.CueParser
import com.example.model.CaseOption
import com.example.model.CueTrack
import com.example.model.SplitOutputConfig
import com.example.model.TargetField
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("TagCue", appName)
    }

    @Test
    fun `parse cue sheet and verify tracks`() {
        val cueText = """
            REM GENRE "Electronic"
            REM DATE 2001
            PERFORMER "Daft Punk"
            TITLE "Discovery"
            FILE "Discovery.flac" WAVE
              TRACK 01 AUDIO
                TITLE "One More Time"
                PERFORMER "Daft Punk"
                INDEX 01 00:00:00
              TRACK 02 AUDIO
                TITLE "Aerodynamic"
                PERFORMER "Daft Punk"
                INDEX 01 05:20:45
        """.trimIndent()

        val cueSheet = CueParser.parseText(cueText)
        assertEquals("Discovery", cueSheet.title)
        assertEquals("Daft Punk", cueSheet.performer)
        assertEquals("2001", cueSheet.date)
        assertEquals(2, cueSheet.tracks.size)

        val track1 = cueSheet.tracks[0]
        assertEquals("One More Time", track1.title)
        assertEquals(1, track1.number)
        assertEquals(0L, track1.startIndex?.totalFrames)

        val track2 = cueSheet.tracks[1]
        assertEquals("Aerodynamic", track2.title)
        assertEquals(2, track2.number)
        assertNotNull(track2.startIndex)
    }

    @Test
    fun `split output config formatting`() {
        val config = SplitOutputConfig(
            filenameTemplate = "{track} - {artist} - {title}",
            trackNumberStyle = com.example.model.TrackNumberStyle.STYLE_01,
            caseOption = CaseOption.TITLE_CASE
        )

        val track = CueTrack(
            number = 3,
            title = "digital love",
            performer = "daft punk",
            album = "discovery"
        )
        val resolved = config.resolveMetadata(track, "discovery", "Daft Punk", "2001", 12)

        assertEquals("03 - Daft Punk - Digital Love.flac", resolved.fileName)
    }

    @Test
    fun `filename to tag parsing`() {
        val parsed = BatchEngine.parseFilenameWithPattern("05 - Daft Punk - Crescendolls", "%n - %a - %t")
        assertEquals(5, parsed.trackNumber)
        assertEquals("Daft Punk", parsed.artist)
        assertEquals("Crescendolls", parsed.title)
    }
}
