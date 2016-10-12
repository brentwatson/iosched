This conversion to [Kotlin](https://kotlinlang.org/) was done as an expermiment to compare Java and
Kotlin compile times, code size, and various other metrics.  Details below.

## Setup

|                | Version      |
|----------------|--------------|
| Android Studio | 2.2.1        |
| Gradle         | 2.2.1        |
| Kotlin         | 1.0.4        |
| OS             | Ubuntu 16.04 |


## Notes

**Please Note**: The code was not throughtly modified to use kotlin paradigms. 
More work could be put in to do better null checking (`!!` instances) and
make better use of the stdlib especially related to collections and iteration.

Only the code under the `android` directory was converted.  Code under `third_party` was not converted.


## Build Times

|                      | clean build       | incremental build |
|----------------------|-------------------|-------------------|
| Java Version         | 42.731 secs       | 6.049 secs        |
| Kotlin gradle config | 47.403 secs       | 5.921 secs        |
| 50% Kotlin Version   | 53.671 secs       | 5.609 secs        |
| Full Kotlin Version  | 1 mins 7.088 secs | 4.965 secs        |

_*Interesting that incremental builds got faster.  This was not expected._

Clean build command: `./gradlew clean android:assembleDebug`  
Incremental build command: `./gradlew android:assembleDebug`


## Code Size

Stats calculated using [cloc](https://github.com/AlDanial/cloc) version 1.70

```bash
> cd ./iosched/android/src/main/java/com/google/samples/apps
> cloc-1.70.pl .
```


### Java Version

```
-------------------------------------------------------------------------------
Language                     files          blank        comment           code
-------------------------------------------------------------------------------
Java                           192           4934           7254          23241
-------------------------------------------------------------------------------
SUM:                           192           4934           7254          23241
-------------------------------------------------------------------------------
```

### 50% Kotlin Version

```
-------------------------------------------------------------------------------
Language                     files          blank        comment           code
-------------------------------------------------------------------------------
Kotlin                         120           2753           4329          12036
Java                            72           2086           2899           9521
-------------------------------------------------------------------------------
SUM:                           192           4839           7228          21557
-------------------------------------------------------------------------------
```

### Full Kotlin Version

```
-------------------------------------------------------------------------------
Language                     files          blank        comment           code
-------------------------------------------------------------------------------
Kotlin                         172           3665           5722          16169
Java                            20           1066           1464           4609
-------------------------------------------------------------------------------
SUM:                           192           4731           7186          20778
-------------------------------------------------------------------------------
```
_* making better use of the stdlib would greatly reduce the final line count._


## Future TODOs

- Convert `for` loops to use stdlib
- Make use of `listOf`, `mapOf`, etc
- Use `with`/`apply` where appropriate
- Find and fix `!!` occurences

## Issues Most Encountered with Java->Kotlin Converter
- Nullable objects treated as non-null and vise-versa
- Complex generic types
- Multi-line string concat (`+` being added on next line)
- Assignments are not expressions (eg `while((x = read()) != -1)`)
- Overzealous companion object creation.


---

Google I/O Android App
======================

Google I/O is a developer conference held each year with two days of deep
technical content featuring technical sessions and hundreds of demonstrations
from developers showcasing their technologies.

This project is the Android app for the conference. The app supports devices
running Android 4.0+, and is optimized for phones and tablets of all shapes
and sizes.

<h2>Source</h2>

The source code in this repository reflects the app as of I/O 2015.

<h2>Features</h2>

With the app, you can:

- View the conference agenda and edit your personal schedule
- Sync your schedule between all of your devices and the I/O website
- View detailed session, code lab, office hours, and speaker information,
  including speaker bios, photos, and Google+ profiles
- Participate in public #io15 conversations on Google+
- Guide yourself using the vector-based conference map
- Get a reminder a few minutes before sessions in your schedule are due to
  start
- Play "I/O Live" session video streams
- Send feedback on sessions, from your phone/tablet.

<h2>How to Work with the Source</h2>

We hope the source code for this app is useful for you as a reference or starting point for creating your own apps. Here is some additional reading to help you better understand and reuse this code.

  * [Build instructions](doc/BUILDING.md): instructions on how to build and run the code.
  * [Sync protocol and data format](doc/SYNC.md)
  * [Image loading](doc/IMAGES.md)
  * [Use of GCM](doc/GCM.md)
  * [Customization guide](doc/CUSTOM.md)

<h2>Copyright</h2>

    Copyright 2014 Google Inc. All rights reserved.

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

        http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
