# Production signing and release builds

The CI debug artifact is a real, signed, sideloadable APK. Production releases need a private, stable signing key owned by the maintainer. Do not commit keystores or passwords. Losing a release key can prevent future updates for installations using that key.

Generate a private key interactively on your own machine:

```sh
keytool -genkeypair -v -keystore stayopen-release.jks -alias stayopen -keyalg RSA -keysize 3072 -validity 10000
```

Provide all four environment variables to Gradle through your shell or private CI secret store:

| Variable | Value |
| --- | --- |
| `STAYOPEN_KEYSTORE` | Absolute path to the private JKS file |
| `STAYOPEN_STORE_PASSWORD` | Keystore password |
| `STAYOPEN_KEY_ALIAS` | `stayopen` or your alias |
| `STAYOPEN_KEY_PASSWORD` | Key password |

Build and verify:

```sh
./gradlew verifyReleaseSigning testDebugUnitTest lintRelease assembleRelease bundleRelease
apksigner verify --verbose --print-certs app/build/outputs/apk/release/app-release.apk
```

The production app ID is `dev.hamweb.stayopen`. The debug app ID is `dev.hamweb.stayopen.debug`. They intentionally install separately. Increment `versionCode` and `versionName` for every production update, retain the same signing key and record the APK SHA-256 hash. Never distribute `app-release-unsigned.apk` as an installation package.

The default workflow uses read-only repository permissions and does not publish releases or handle production secrets. Add a protected release environment and the above secrets if automated production distribution is wanted. The emulator / debug workflow must never receive the production keystore.

Before public release, test the physical Samsung variants and menu paths listed in TESTING.md, review optional usage access against the intended store policy, and run a signed release on real hardware. No Play Store publication or physical Samsung certification is implied by a debug build.
