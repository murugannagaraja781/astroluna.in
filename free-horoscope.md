---
description: Comprehensive workflow for generating a Free Horoscope chart (Android & Web)
---

# Free Horoscope Workflow

Follow these steps to verify or implement the "Free Horoscope" chart feature.

## 1. Data Collection (Android)
- Open `FreeHoroscopeActivity.kt`.
- Enter birth details (Name, Gender, DOB, TOB, POB).
- Ensure Timezone computes correctly based on City selection.
- Click "Generate Rasi Chart".

## 2. API Verification
- The app calls `POST /api/rasi-eng/charts/full`.
- Payload format:
  ```json
  {
    "date": "YYYY-MM-DD",
    "time": "HH:MM",
    "lat": 13.0827,
    "lng": 80.2707,
    "timezone": 5.5
  }
  ```
- Use `node test_chart_api.js` to verify the backend endpoint is responsive.

## 3. UI Rendering (Traditional Aesthetic)
- Open `VipChartActivity.kt`.
- Verify `ParchmentBase` (`0xFFF4E1C1`) and `TraditionalRed` (`0x8B0000`).
- Check `SouthIndianGridEnhanced` for planet abbreviations and degrees.
- Verify Tamil translations for signs and planets in `signTamil` and `planetTamil` maps.

## 4. Web Frontend (Optional)
- Open `public/index.html`.
- Search for `screen-chart-helper` modal.
- Verify `displayDashboardChartResults` handles the API response.
