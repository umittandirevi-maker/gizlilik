# FocusAI Rebranding, UI Streamlining & App Whitelist Refinement

Streamline the application under the brand name **FocusAI**, remove the testing simulator section to deliver a clean production-grade Home screen, and refine the "Always Allow Apps" list to show only genuine user-installed applications with verified app icons while hiding background system packages.

## User Review & Critical Decisions

> [!IMPORTANT]
> The following product decisions were confirmed during the interactive interview:
> - **App Name**: Renamed to **FocusAI** across platform metadata (`metadata.json`), Android app label (`strings.xml`), build configuration (`settings.gradle.kts`), and UI headers.
> - **System App Filtering**: All Android OS and carrier system packages are strictly excluded, except essential default communication utilities (Phone/Dialer and Default SMS/Messages).
> - **App Icon Enforcement**: Any application entry lacking a verified, valid app icon is strictly filtered out of the list.
> - **Home Screen Focus**: The "Linguistic AI Simulator" panel is completely removed. The Home screen now features a clean, focused hierarchy: Notification Access card (if required), 180dp circular filter button, dynamic "Mute Active Siren" button when alerting, and the live notification classification stream.

---

## 1. Overview & Core Concept

- **What It Does**:
  1. Rebrands the entire project to **FocusAI** with consistent visual identity.
  2. Eliminates developer/test simulation UI elements from the consumer experience.
  3. Curates the "Always Allow Apps" screen into an uncluttered list containing only real, icon-backed user apps and essential communication tools with unchecked checkboxes.
- **Key Value**: Provides a distraction-free, premium product experience that feels lightweight and purpose-built for notification focus and safety.

---

## 2. User Experience & Visual Design

### Screen Layout After Streamlining

```
┌────────────────────────────────────────────────────────────────────────┐
│                        Top Header: FocusAI                             │
│   FocusAI  [100% OFFLINE]                    [Master Siren Toggle]     │
└────────────────────────────────────────────────────────────────────────┘
                                    │
    ┌───────────────────────────────┼───────────────────────────────┐
    ▼                               ▼                               ▼
[Tab 1: Home]             [Tab 2: Allowed Apps]           [Tab 3: Settings]
• Permission Banner       • Search Bar                    • Category Rules
• 180dp Filter Circle     • X Apps Allowed Counter        • Dual Switches
• Dynamic Mute Siren      • Only Apps With Real Icons     • Dependency Lock
• Recent Notifications    • System Background Apps Hidden • Engine Info
• Clean Minimal Canvas    • Unchecked Checkboxes
                                    │
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│                     Material 3 NavigationBar                           │
│      [🏠 Home]         [🛡️ Always Allow]           [⚙️ Settings]       │
└────────────────────────────────────────────────────────────────────────┘
```

1. **FocusAI Header**: Displays the prominent title "FocusAI" with the emerald "100% OFFLINE" pill and master siren mute toggle.
2. **Simplified Home Screen**:
   - Zero simulation clutter.
   - Immediate focus on the circular state button with smooth pulse animations.
   - Real-time notification log displaying recently intercepted notifications with classification badges and explainability summaries.
3. **Pristine Always Allow Apps Screen**:
   - Only displays downloaded applications (social, messaging, utilities) and core phone/messaging tools.
   - Every single displayed row features a crisp, authentic app icon.
   - Clean M3 checkboxes initialized in an unchecked state.

---

## 3. Key Product Decisions & Trade-Offs

- **Decision 1: Rebranding Platform & Resource Synchronization**
  - *Chosen Approach*: Update `metadata.json`, `app/src/main/res/values/strings.xml`, `settings.gradle.kts`, and all Composable headers simultaneously to "FocusAI".
  - *Why*: Adheres strictly to the platform synchronization rule so the sidebar, launcher, and in-app headers match seamlessly.
- **Decision 2: Strict Icon-Backed App Filtering**
  - *Chosen Approach*: In `MainViewModel.loadInstalledApps()`, filter entries to `app.iconBitmap != null && (!isSystemApp || isEssentialPhoneOrSms(pkg))`.
  - *Why*: Eliminates confusing system daemons, overlay services, and icon-less framework packages while guaranteeing every item has visual identity.
- **Decision 3: Complete Simulator Elimination from UI**
  - *Chosen Approach*: Remove `SimulationSection` from `HomeScreen.kt` entirely.
  - *Why*: Directly satisfies the requirement to leave nothing extra in the user-facing app.

---

## 4. Technical Architecture & Data Strategy

### Component Adjustments

```
┌────────────────────────────────────────────────────────┐
│               SmartFilterScreen (Host)                 │
│  Header: "FocusAI" • Master Siren Icon • NavigationBar │
└───────────┬──────────────────┬──────────────────┬──────┘
            │                  │                  │
            ▼                  ▼                  ▼
    ┌──────────────┐   ┌───────────────┐   ┌──────────────┐
    │  HomeScreen  │   │  AllowedApps  │   │   Settings   │
    │  • 180dp FAB │   │  • Filtered   │   │  • Category  │
    │  • Mute Btn  │   │    apps with  │   │    rules &   │
    │  • Logs only │   │    real icons │   │    locks     │
    └──────────────┘   └───────────────┘   └──────────────┘
```

- **`metadata.json`**: `"name": "FocusAI"`
- **`strings.xml`**: `<string name="app_name">FocusAI</string>`
- **`settings.gradle.kts`**: `rootProject.name = "FocusAI"`
- **`MainViewModel.kt`**: Update app query logic to discard non-icon packages and non-essential system apps.
- **`HomeScreen.kt`**: Remove `SimulationSection` call.
