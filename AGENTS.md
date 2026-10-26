# Project checks

Write all source and resource files as UTF-8. Keep Android application names in the variant strings.xml files rather than generating Chinese strings in Gradle.

| Rule | Enforcement |
| --- | --- |
| Application and launcher names must display correctly in both APK variants | `python scripts/check_app_names.py` and CI compiled APK label checks plus emulator app-info screenshot |
