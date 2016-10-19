# datauploader-android

## Introduction
This is an Android application which uploads vital sign data from Google fit to a SMART compatible FHIR resource server.

## Development
1. Installl the latest version of the [Android SDK](http://developer.android.com).
2. Clone this repository using `git clone git@github.com:sll-mdilab/t5-android-app.git`.
3. Import the project into android studio using `File -> New -> Import Project`.

## Confguration
The FHIR server to which vital sign data should be uploaded is configured as the string value `fhir_base_url` in the `app/src/main/res/values/strings.xml`-file.

