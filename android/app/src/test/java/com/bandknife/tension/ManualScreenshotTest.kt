package com.bandknife.tension

import androidx.compose.runtime.Composable
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.bandknife.tension.ui.manual.ManualAdvanceResultPreview
import com.bandknife.tension.ui.manual.ManualDetailEquipmentPreview
import com.bandknife.tension.ui.manual.ManualDetailHistoryPreview
import com.bandknife.tension.ui.manual.ManualDetailMeasurePreview
import com.bandknife.tension.ui.manual.ManualDetailSettingsPreview
import com.bandknife.tension.ui.manual.ManualSimpleEquipmentSelectPreview
import com.bandknife.tension.ui.manual.ManualSimpleMeasuringPreview
import com.bandknife.tension.ui.manual.ManualSimpleResultPreview
import com.bandknife.tension.ui.manual.ManualSpecSettingsPreview
import com.bandknife.tension.ui.theme.BandKnifeTheme
import org.junit.Rule
import org.junit.Test

class ManualScreenshotTest {
    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5.copy(locale = "ja"),
        theme = "android:Theme.Material.Light.NoActionBar"
    )

    @Test fun `01 simple equipment select`() = snapshot { ManualSimpleEquipmentSelectPreview() }

    @Test fun `02 simple measuring`() = snapshot { ManualSimpleMeasuringPreview() }

    @Test fun `03 simple result`() = snapshot { ManualSimpleResultPreview() }

    @Test fun `04 advance result`() = snapshot { ManualAdvanceResultPreview() }

    @Test fun `05 detail measure`() = snapshot { ManualDetailMeasurePreview() }

    @Test fun `06 detail equipment`() = snapshot { ManualDetailEquipmentPreview() }

    @Test fun `07 detail history`() = snapshot { ManualDetailHistoryPreview() }

    @Test fun `08 detail settings`() = snapshot { ManualDetailSettingsPreview() }

    @Test fun `09 spec settings`() = snapshot { ManualSpecSettingsPreview() }

    private fun snapshot(content: @Composable () -> Unit) {
        paparazzi.snapshot {
            BandKnifeTheme { content() }
        }
    }
}
