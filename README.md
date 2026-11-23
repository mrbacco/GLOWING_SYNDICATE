<!--
File:     README.md
Author:   mrbacco04@gmail.com
GitHub:   https://github.com/mrbacco
Created:  2026-10-20
Updated:  2026-11-23
Project:  SYNDICATE
License:  Proprietary, all rights reserved (see LICENSE)
-->
# SYNDICATE

An app that runs a lotto syndicate for the organiser: it tracks who's in,
who's paid, which numbers are played, and checks every draw automatically.

The product plan is kept privately and is not part of this repository.

## Project layout

```
SYNDICATE/
├── README.md                    this file
├── LICENSE                      license: all rights reserved
└── android/                     Kotlin + Jetpack Compose app (organiser);
                                 open THIS folder in Android Studio
```

## Build steps

1. Project skeleton ✔
2. Android app with sample data: syndicates, members, payments, lines, results ✔
3. Python backend: data model, database, API ✔ (later removed, see 7)
4. Results checking, with a results scraper for lottery.ie ✔
5. Member web page (served by the backend) ✔ (removed with the backend)
6. Connect the Android app to the backend ✔
7. Local-first: the app keeps its data on the phone and fetches results itself; the backend is
   removed (it is still in the git history) ✔

## File headers

Every source file starts with a header like this:

```
File:     <path inside the project>
Author:   mrbacco04@gmail.com
GitHub:   https://github.com/mrbacco
Created:  <date the file was created>
Updated:  <date of the last change>
Project:  SYNDICATE
License:  Proprietary, all rights reserved (see LICENSE)
```

Exceptions: JSON files (JSON has no comments) and the license texts, which
are kept verbatim.

## License

Copyright (c) 2026 mrbacco. All rights reserved.

The source code is public so that people can read it, but it is not open
source: you may not copy, change, run or distribute it without a written
license from the copyright holder. See `LICENSE`. Third-party files (the
Gradle wrapper and the Open Sans font) keep their own licenses.
