# Privacy Policy for FocusAI

**Effective Date:** October 24, 2026  
**Last Updated:** October 24, 2026  
**Developer / Contact:** fixbangstudio@gmail.com  

FocusAI ("we", "our", or "us") provides the **FocusAI** Android application. We are committed to protecting your privacy and ensuring you have complete control over your personal data.

This Privacy Policy explains how FocusAI handles your information, specifically our use of sensitive permissions such as Android Notification Access.

---

## 1. Core Principle: 100% On-Device Processing

FocusAI is designed with **Privacy by Design** as its foundation:
- **No Cloud Uploads:** Your notifications, messages, contact names, and app usage details are processed **entirely on your local device**.
- **No Remote Servers:** We do not operate remote servers that collect, store, transmit, or analyze your notifications.
- **No Sale of Personal Data:** We do not sell, rent, monetize, or disclose your personal data or notification content to any advertisers, data brokers, or third parties.

---

## 2. Sensitive Permissions & How They Are Used

To deliver its core notification filtering and emergency alerting functions, FocusAI requires specific Android permissions. Each permission is used strictly for its declared purpose:

### A. Notification Listener Service (`BIND_NOTIFICATION_LISTENER_SERVICE`)
- **Purpose:** Allows FocusAI to inspect incoming notification headers, titles, and text locally in real-time.
- **Usage:** The app evaluates incoming notifications against your local filter rules (e.g., emergency keywords, priority contacts, and your "Always Allow Apps" selections) to determine whether an audible emergency alert or silent pass-through should occur.
- **Data Retention:** Notification inspection happens ephemerally in memory (RAM). Raw notification text is discarded immediately after classification and is never logged to external servers or remote telemetry.

### B. Post Notifications (`POST_NOTIFICATIONS`)
- **Purpose:** On Android 13 (API level 33) and above, this permission is required to display high-priority emergency alerts and notifications from FocusAI when an urgent condition is triggered.

### C. Access Do Not Disturb / Notification Policy (`ACCESS_NOTIFICATION_POLICY`) & Vibrate (`VIBRATE`)
- **Purpose:** Allows FocusAI to bypass Do Not Disturb (DND) modes and trigger vibration patterns only when a confirmed critical or emergency notification occurs, ensuring life-safety and urgent alerts reach you.

### D. Query All Packages (`QUERY_ALL_PACKAGES`)
- **Purpose:** Allows FocusAI to display installed applications (e.g., messaging and communication apps) in the "Always Allow Apps" configuration screen so you can easily choose which apps bypass filtering.
- **Usage:** Package information is used strictly for rendering your local app list with corresponding app icons and names. It is never uploaded or shared.

---

## 3. Information We Collect

- **User Preferences & Configuration:** User-defined rules, custom emergency keywords, siren volume preferences, and selected "Always Allow" apps are stored exclusively in private local app storage on your device.
- **Diagnostics & Crashes:** FocusAI does not bundle third-party tracking, advertising SDKs, or background telemetry.

---

## 4. Third-Party Services & Data Sharing

FocusAI does not share your data with third parties:
- **No Advertising SDKs:** The application does not contain advertising libraries.
- **No Analytics / Tracking SDKs:** We do not track your behavior across apps or websites.
- **No Third-Party Cloud Backends:** All AI heuristics and keyword evaluations run on-device.

---

## 5. Security & Data Retention

- All configuration data is stored within Android’s secure internal sandbox directory (`/data/data/com.aistudio.smartnotificationfilter.xrwv/`).
- If you uninstall FocusAI or clear application data in Android Settings, all locally stored preferences and logs are permanently erased from your device.

---

## 6. Managing Your Permissions

You have full control over FocusAI permissions at any time:
1. Open Android **Settings** > **Apps** (or **Special App Access**).
2. Select **Notification Access** (or **Notification Read & Reply**).
3. Toggle FocusAI ON or OFF according to your preference.
4. You can also revoke notification, DND, or background permissions at any time in Android Settings > Apps > FocusAI > Permissions.

---

## 7. Children's Privacy

FocusAI is a general utility tool and does not knowingly collect or solicit personal information from children under the age of 13.

---

## 8. Changes to This Privacy Policy

We may update this Privacy Policy from time to time. Any changes will be posted at this URL and updated within the application repository.

---

## 9. Contact Us

If you have any questions, feedback, or concerns regarding this Privacy Policy or FocusAI's privacy practices, please contact us at:

**Email:** fixbangstudio@gmail.com  
**Developer:** FocusAI Studio
