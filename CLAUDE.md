# CLAUDE.md

## Project Guidelines

- Always commit major changes, but only after the feature has been tested and confirmed working 
- Do not include yourself (Co-Authored-By) in commit messages
- Always write a test when fixing a bug

## Debugging

Use adb from Android SDK (not in PATH):
```bash
~/Android/Sdk/platform-tools/adb devices
~/Android/Sdk/platform-tools/adb logcat -d -t 100 | grep tablethub
~/Android/Sdk/platform-tools/adb logcat --pid=$(~/Android/Sdk/platform-tools/adb shell pidof net.damian.tablethub)
```

## Building

Use Java 21 (Java 25 has Hilt compatibility issues):
```bash
JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew assembleDebug
```

## Documentation

- `README.md` - Project overview
- `tablet-hub-spec.md` - Full specification
- `tablet-hub-post-mvp.md` - Future enhancements
