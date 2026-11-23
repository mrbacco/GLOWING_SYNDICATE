<!--
File:     android/README.md
Author:   mrbacco04@gmail.com
GitHub:   https://github.com/mrbacco
Created:  2026-10-20
Updated:  2026-11-23
Project:  SYNDICATE
License:  Proprietary, all rights reserved (see LICENSE)
-->
# SYNDICATE Android app

Kotlin + Jetpack Compose app for the syndicate organiser.
Package name: `com.mrbacco.syndicate`

The app works on its own, with no server and no account. Everything the organiser enters is kept
on the phone in one JSON file (`syndicate-data.json` in the app's private storage, included in
Android's backup). The only thing fetched from the internet is the draw results: after each
Monday, Wednesday and Saturday draw, the app reads them from the results history page on
lottery.ie when a syndicate is opened, or when Refresh is tapped.

## Open and run

1. Android Studio → **File → Open** → select this `android` folder, and wait for **Gradle sync**.
2. Connect a phone (USB or wireless debugging) or start an emulator.
3. Press **Run ▶**.

 right-click `app/src/test` → **Run 'Tests in ...'**, or run
`gradlew test` from a terminal in this folder.

## Code layout

```
app/src/main/java/com/mrbacco/syndicate/
├── MainActivity.kt              app entry point
├── SyndicateApp.kt              starts AppContainer when the app starts
├── data/
│   ├── local/                   the saved data (Stored.kt) and the JSON file it lives in (LocalStore.kt)
│   ├── results/                 reads lottery.ie (LotteryIe.kt); when a result is due, merging it (Results.kt)
│   ├── model/Screens.kt         what each screen shows
│   ├── ScreenBuilder.kt         works out and words each screen from the saved data
│   ├── SyndicateRepository.kt   the one entry point for screens: reads, changes, fetches results
│   ├── AppPrefs.kt              small settings ("don't show this again")
│   └── AppContainer.kt          creates the repository
├── domain/
│   ├── Lotto.kt                 Lotto rules by date (6/47, then 6/45), line checking, prize tiers
│   ├── Weeks.kt                 weeks start on Monday; who owes what
│   ├── Winnings.kt              who shares a win and how much each gets
│   ├── Texts.kt                 money, dates and member messages
│   ├── LineInput.kt             reads a typed line
│   ├── Money.kt                 reads typed euro amounts
│   └── PhoneNumbers.kt          phone numbers for WhatsApp links
└── ui/
    ├── theme/Theme.kt           pastel grey and yellow colours, light and dark; narrow font
    ├── navigation/SyndicateNavHost.kt  screen navigation
    ├── components/              number balls, input dialog, loading and error states
    ├── welcome/                 landing page, shown each time the app opens (unless turned off)
    ├── list/                    "My syndicates" screen
    └── detail/                  Members / Lines / Results tabs, add member dialog
```

## Versions

Android Gradle Plugin 9.5.0, Gradle 9.6.0, Kotlin 2.2.10, Compose BOM 2024.12.01,
kotlinx.serialization 1.9.0, compileSdk/targetSdk 36, minSdk 26 (Android 8.0).

## Font

The app uses Open Sans at its narrowest width (`res/font/open_sans.ttf`, a variable font), in place
of Aptos Narrow, which is licensed with Microsoft Office and cannot be shipped in the app. Open Sans
is under the SIL Open Font License 1.1; its licence is in `app/licenses/OpenSans-OFL.txt`.
