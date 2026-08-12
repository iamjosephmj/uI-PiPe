# Certification Demo Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rebuild sample-host (Compose + Material 3, MVVM) and sample-provider (Material-themed pane, presenter + Keystore data layer) around a real certification flow: host sends a nonce, provider's consent pane signs it with a hardware-attested AndroidKeyStore key, host verifies signature + attestation chain.

**Architecture:** New `:sample-contract` JVM module holds `@Serializable` messages carried by `:pipe-serialization` (CBOR). Host: ViewModel → use cases (`RequestCertificationUseCase`, `VerifyCertificationUseCase`) → data (`PipeSessionRepository`, `CertificationVerifier` with a hand-rolled minimal DER parser). Provider: `PanePresenter` state machine → `IssueCertificationUseCase` → `KeystoreRepository`. Manual DI via small containers.

**Tech Stack:** Kotlin 2.2.10, Compose BOM 2025.06.01 + Material 3, kotlinx-serialization-cbor, AndroidKeyStore key attestation, JUnit4 (+ BouncyCastle test-only for cert fixtures), UiAutomator e2e on Pixel 6 Pro `19011FDEE0040L`.

**Spec:** `docs/superpowers/specs/2026-08-12-certification-demo-design.md`

## Global Constraints

- minSdk 35, compileSdk/targetSdk 36, JVM target 17 — same as every module in the repo.
- Test-anchor strings that MUST survive verbatim: `pane-ready` (provider idle caption), `denied: ` prefix on host error text (incl. exact `denied: async-policy`), `EVIL-PANE` (evil provider pane).
- `MainActivity` must keep honoring intent extras `targetPackage`, `targetService`, `targetAuthorizer` (`"suspend-deny"` → `yield()` then `AuthDecision.Deny("async-policy")`) — the security suite launches it with these.
- Both sample apps stay debug-signed with the default debug key; host default authorizer is `PipeAuthorizers.sameSigningKey`.
- Typed messages MUST be sent with the sealed supertype (`send<CertificationResponse>(...)`) — `PipeCodec.decodeOrNull<T>` matches `T::class.qualifiedName` exactly, so a message encoded as `Granted` would not decode as `CertificationResponse`.
- Unknown attestation root or missing attestation extension degrades the badge (Software-backed), never hard-fails verification.
- Device gate: full connected suite green on Pixel 6 Pro serial `19011FDEE0040L` (`adb shell input keyevent KEYCODE_WAKEUP && adb shell wm dismiss-keyguard` first).
- `JAVA_HOME=~/.jdks/temurin-23.0.2` for all Gradle commands.
- Commit messages end with `Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>`.

## File Structure

```
sample-contract/                            (NEW kotlin-jvm module)
  build.gradle.kts
  src/main/kotlin/tech/ssemaj/pipe/samples/contract/CertificationContract.kt
  src/test/kotlin/tech/ssemaj/pipe/samples/contract/ContractRoundTripTest.kt
sample-host/
  build.gradle.kts                          (compose, serialization deps)
  src/main/AndroidManifest.xml              (MultiPaneActivity, Material3 theme)
  src/main/java/tech/ssemaj/pipe/samplehost/
    MainActivity.kt                         (rewritten: ComponentActivity + setContent)
    MultiPaneActivity.kt                    (NEW, plain XML, two PipeViews)
    di/AppContainer.kt
    presentation/CertificationViewModel.kt
    presentation/UiState.kt
    presentation/ui/Theme.kt
    presentation/ui/CertificationScreen.kt
    domain/RequestCertificationUseCase.kt
    domain/VerifyCertificationUseCase.kt
    domain/CertificationResult.kt
    data/PipeSessionRepository.kt
    data/CertificationVerifier.kt
    data/Der.kt
    data/AttestationParser.kt
  src/main/res/layout/activity_multi_pane.xml
  src/main/res/values/themes.xml
  src/test/java/tech/ssemaj/pipe/samplehost/   (JVM unit tests)
    DerTest.kt  AttestationParserTest.kt  CertificationVerifierTest.kt  RequestCertificationUseCaseTest.kt
  src/androidTest/java/tech/ssemaj/pipe/samplehost/
    PipeE2eTest.kt (rewritten)  MultiPaneE2eTest.kt (NEW)  ReopenE2eTest.kt (NEW)
    SecurityE2eTest.kt (unchanged)  SuspendAuthorizerDenialTest.kt (unchanged)
sample-provider/
  build.gradle.kts                          (serialization, material, androidTest runner)
  src/main/java/tech/ssemaj/pipe/sampleprovider/
    DemoPaneService.kt                      (rewritten)
    pane/PanePresenter.kt
    domain/IssueCertificationUseCase.kt
    data/KeystoreRepository.kt
  src/main/res/values/themes.xml
  src/test/java/tech/ssemaj/pipe/sampleprovider/PanePresenterTest.kt
  src/androidTest/java/tech/ssemaj/pipe/sampleprovider/KeystoreRepositoryTest.kt
gradle/libs.versions.toml                   (compose, material, BC entries)
settings.gradle.kts                         (include :sample-contract)
```

---

### Task 1: Build scaffolding + `:sample-contract` module with CBOR round-trip test

**Files:**
- Modify: `gradle/libs.versions.toml`, `settings.gradle.kts`, `sample-host/build.gradle.kts`, `sample-provider/build.gradle.kts`
- Create: `sample-contract/build.gradle.kts`, `sample-contract/src/main/kotlin/tech/ssemaj/pipe/samples/contract/CertificationContract.kt`
- Test: `sample-contract/src/test/kotlin/tech/ssemaj/pipe/samples/contract/ContractRoundTripTest.kt`

**Interfaces:**
- Produces (used by every later task): `tech.ssemaj.pipe.samples.contract.CertificationRequest(nonce: ByteArray, hostDisplayName: String)`, sealed `CertificationResponse` with `Granted(signature: ByteArray, certChainDer: List<ByteArray>, securityLevel: SecurityLevel)` / `Declined(reason: String)`, `enum SecurityLevel { HARDWARE, SOFTWARE }`, `const ACTION_CERTIFICATION = "pipe.demo.certification"`.

- [ ] **Step 1: Add catalog entries** to `gradle/libs.versions.toml`:

```toml
# under [versions]
composeBom = "2025.06.01"
activityCompose = "1.10.1"
material = "1.12.0"
bouncycastle = "1.78.1"

# under [libraries]
compose-bom = { module = "androidx.compose:compose-bom", version.ref = "composeBom" }
compose-material3 = { module = "androidx.compose.material3:material3" }
compose-ui = { module = "androidx.compose.ui:ui" }
compose-foundation = { module = "androidx.compose.foundation:foundation" }
activity-compose = { module = "androidx.activity:activity-compose", version.ref = "activityCompose" }
lifecycle-viewmodel-compose = { module = "androidx.lifecycle:lifecycle-viewmodel-compose", version = "2.8.7" }
material-components = { module = "com.google.android.material:material", version.ref = "material" }
bcpkix = { module = "org.bouncycastle:bcpkix-jdk18on", version.ref = "bouncycastle" }

# under [plugins]
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
kotlin-jvm = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }
```

- [ ] **Step 2: Register the module** — in `settings.gradle.kts` add `include(":sample-contract")` after the `:pipe-serialization` line.

- [ ] **Step 3: Write the failing round-trip test** at `sample-contract/src/test/kotlin/tech/ssemaj/pipe/samples/contract/ContractRoundTripTest.kt`:

```kotlin
package tech.ssemaj.pipe.samples.contract

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalSerializationApi::class)
class ContractRoundTripTest {

    @Test fun requestRoundTrips() {
        val req = CertificationRequest(nonce = ByteArray(32) { it.toByte() }, hostDisplayName = "Pipe Sample Host")
        val back = Cbor.decodeFromByteArray<CertificationRequest>(Cbor.encodeToByteArray(req))
        assertArrayEquals(req.nonce, back.nonce)
        assertEquals("Pipe Sample Host", back.hostDisplayName)
    }

    @Test fun grantedRoundTripsThroughSealedSupertype() {
        val granted: CertificationResponse = CertificationResponse.Granted(
            signature = byteArrayOf(1, 2, 3),
            certChainDer = listOf(byteArrayOf(4, 5), byteArrayOf(6)),
            securityLevel = SecurityLevel.HARDWARE,
        )
        val back = Cbor.decodeFromByteArray<CertificationResponse>(Cbor.encodeToByteArray(granted))
        assertTrue(back is CertificationResponse.Granted)
        back as CertificationResponse.Granted
        assertArrayEquals(byteArrayOf(1, 2, 3), back.signature)
        assertEquals(2, back.certChainDer.size)
        assertEquals(SecurityLevel.HARDWARE, back.securityLevel)
    }

    @Test fun declinedRoundTripsThroughSealedSupertype() {
        val declined: CertificationResponse = CertificationResponse.Declined("user said no")
        val back = Cbor.decodeFromByteArray<CertificationResponse>(Cbor.encodeToByteArray(declined))
        assertTrue(back is CertificationResponse.Declined)
        assertEquals("user said no", (back as CertificationResponse.Declined).reason)
    }
}
```

- [ ] **Step 4: Create the module.** `sample-contract/build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

java { toolchain { languageVersion.set(JavaLanguageVersion.of(17)) } }

dependencies {
    api(libs.kotlinx.serialization.cbor)
    testImplementation(libs.junit)
}
```

`sample-contract/src/main/kotlin/tech/ssemaj/pipe/samples/contract/CertificationContract.kt`:

```kotlin
package tech.ssemaj.pipe.samples.contract

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Pane action the host requests and the provider's manifest/service handles. */
const val ACTION_CERTIFICATION = "pipe.demo.certification"

/** Host → provider: certify possession of a device key by signing [nonce]. */
@Serializable
class CertificationRequest(val nonce: ByteArray, val hostDisplayName: String)

/** Provider → host. Always send/collect as the sealed supertype (see PipeCodec type matching). */
@Serializable
sealed interface CertificationResponse {
    @Serializable
    @SerialName("granted")
    class Granted(
        val signature: ByteArray,
        val certChainDer: List<ByteArray>,
        /** Provider's claim only — the host verifies independently from the chain. */
        val securityLevel: SecurityLevel,
    ) : CertificationResponse

    @Serializable
    @SerialName("declined")
    class Declined(val reason: String) : CertificationResponse
}

@Serializable
enum class SecurityLevel { HARDWARE, SOFTWARE }
```

- [ ] **Step 5: Run the test** — `JAVA_HOME=~/.jdks/temurin-23.0.2 ./gradlew :sample-contract:test`; expect PASS (3 tests).

- [ ] **Step 6: Wire app dependencies.** In `sample-host/build.gradle.kts`: add plugins `alias(libs.plugins.kotlin.compose)`; inside `android {}` add `buildFeatures { compose = true }`; dependencies add:

```kotlin
implementation(project(":pipe-serialization"))
implementation(project(":sample-contract"))
implementation(platform(libs.compose.bom))
implementation(libs.compose.ui)
implementation(libs.compose.foundation)
implementation(libs.compose.material3)
implementation(libs.activity.compose)
implementation(libs.lifecycle.viewmodel.compose)
implementation(libs.kotlinx.serialization.cbor)
testImplementation(libs.junit)
testImplementation(libs.coroutines.test)
testImplementation(libs.bcpkix)
```

In `sample-provider/build.gradle.kts`: add `testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"` to `defaultConfig`; dependencies add:

```kotlin
implementation(project(":pipe-serialization"))
implementation(project(":sample-contract"))
implementation(libs.coroutines.android)
implementation(libs.material.components)
implementation(libs.kotlinx.serialization.cbor)
testImplementation(libs.junit)
testImplementation(libs.coroutines.test)
androidTestImplementation(libs.androidx.test.ext)
androidTestImplementation(libs.androidx.test.runner)
```

(Keep existing deps. sample-host already has coroutines + lifecycle-runtime.)

- [ ] **Step 7: Verify everything still assembles** — `./gradlew :sample-host:assembleDebug :sample-provider:assembleDebug`; expect BUILD SUCCESSFUL (apps unchanged so far, just new deps).

- [ ] **Step 8: Commit**

```bash
git add -A && git commit -m "feat: add sample-contract module and demo build scaffolding"
```

---

### Task 2: Minimal DER parser + attestation-extension parser (host data layer)

**Files:**
- Create: `sample-host/src/main/java/tech/ssemaj/pipe/samplehost/data/Der.kt`, `.../data/AttestationParser.kt`
- Test: `sample-host/src/test/java/tech/ssemaj/pipe/samplehost/DerTest.kt`, `.../AttestationParserTest.kt`

**Interfaces:**
- Produces: `internal object Der { data class Tlv(tag: Int, value: ByteArray, end: Int); fun parse(bytes: ByteArray, offset: Int): Tlv; fun children(seq: ByteArray): List<Tlv> }`; `internal data class AttestationInfo(challenge: ByteArray, securityLevel: Int)` (0=software, 1=TEE, 2=StrongBox); `internal object AttestationParser { const val OID; fun parse(leaf: X509Certificate): AttestationInfo? }` — `null` when the extension is absent/malformed.

- [ ] **Step 1: Write the failing DER tests** at `sample-host/src/test/java/tech/ssemaj/pipe/samplehost/DerTest.kt`:

```kotlin
package tech.ssemaj.pipe.samplehost

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import tech.ssemaj.pipe.samplehost.data.Der

class DerTest {

    @Test fun parsesShortFormLength() {
        // OCTET STRING (0x04), length 3, payload 01 02 03
        val tlv = Der.parse(byteArrayOf(0x04, 0x03, 0x01, 0x02, 0x03), 0)
        assertEquals(0x04, tlv.tag)
        assertArrayEquals(byteArrayOf(0x01, 0x02, 0x03), tlv.value)
        assertEquals(5, tlv.end)
    }

    @Test fun parsesLongFormLength() {
        // OCTET STRING, long-form length 0x81 0x80 = 128 bytes
        val payload = ByteArray(128) { 0x5A }
        val tlv = Der.parse(byteArrayOf(0x04, 0x81.toByte(), 0x80.toByte()) + payload, 0)
        assertEquals(128, tlv.value.size)
        assertEquals(131, tlv.end)
    }

    @Test fun walksSequenceChildren() {
        // SEQUENCE { INTEGER 7, OCTET STRING AB }
        val seq = byteArrayOf(0x02, 0x01, 0x07, 0x04, 0x01, 0xAB.toByte())
        val kids = Der.children(seq)
        assertEquals(2, kids.size)
        assertEquals(0x02, kids[0].tag)
        assertArrayEquals(byteArrayOf(0x07), kids[0].value)
        assertEquals(0x04, kids[1].tag)
        assertArrayEquals(byteArrayOf(0xAB.toByte()), kids[1].value)
    }
}
```

- [ ] **Step 2: Run to verify fail** — `./gradlew :sample-host:testDebugUnitTest --tests "tech.ssemaj.pipe.samplehost.DerTest"`; expect compile failure (unresolved `Der`).

- [ ] **Step 3: Implement `Der.kt`:**

```kotlin
package tech.ssemaj.pipe.samplehost.data

/** Just enough DER to walk the Android key-attestation KeyDescription sequence. Not general-purpose. */
internal object Der {
    internal data class Tlv(val tag: Int, val value: ByteArray, val end: Int)

    internal fun parse(bytes: ByteArray, offset: Int): Tlv {
        val tag = bytes[offset].toInt() and 0xFF
        var i = offset + 1
        var len = bytes[i].toInt() and 0xFF
        i++
        if (len and 0x80 != 0) {
            val n = len and 0x7F
            require(n in 1..4) { "unsupported DER length-of-length: $n" }
            len = 0
            repeat(n) {
                len = (len shl 8) or (bytes[i].toInt() and 0xFF)
                i++
            }
        }
        require(i + len <= bytes.size) { "DER length overruns buffer" }
        return Tlv(tag, bytes.copyOfRange(i, i + len), i + len)
    }

    internal fun children(sequenceValue: ByteArray): List<Tlv> {
        val out = mutableListOf<Tlv>()
        var i = 0
        while (i < sequenceValue.size) {
            val tlv = parse(sequenceValue, i)
            out.add(tlv)
            i = tlv.end
        }
        return out
    }
}
```

- [ ] **Step 4: Run to verify pass** — same command; expect PASS (3 tests).

- [ ] **Step 5: Write the failing attestation-parser test** at `.../AttestationParserTest.kt`. It hand-builds a KeyDescription DER (`SEQUENCE { INTEGER attestationVersion, ENUMERATED attSecurityLevel, INTEGER kmVersion, ENUMERATED kmSecurityLevel, OCTET STRING challenge, OCTET STRING uniqueId, SEQUENCE sw, SEQUENCE tee }`) and exercises `parseKeyDescription` (the pure core; the `X509Certificate` overload is a thin unwrap tested via Task 3's fixtures):

```kotlin
package tech.ssemaj.pipe.samplehost

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import tech.ssemaj.pipe.samplehost.data.AttestationParser

private fun tlv(tag: Int, value: ByteArray): ByteArray {
    require(value.size < 128)
    return byteArrayOf(tag.toByte(), value.size.toByte()) + value
}

private fun keyDescription(challenge: ByteArray, securityLevel: Int): ByteArray {
    val body = tlv(0x02, byteArrayOf(100)) +            // attestationVersion
        tlv(0x0A, byteArrayOf(securityLevel.toByte())) + // attestationSecurityLevel
        tlv(0x02, byteArrayOf(100)) +                    // keymasterVersion
        tlv(0x0A, byteArrayOf(securityLevel.toByte())) + // keymasterSecurityLevel
        tlv(0x04, challenge) +                           // attestationChallenge
        tlv(0x04, ByteArray(0)) +                        // uniqueId
        tlv(0x30, ByteArray(0)) +                        // softwareEnforced
        tlv(0x30, ByteArray(0))                          // teeEnforced
    return tlv(0x30, body)
}

class AttestationParserTest {

    @Test fun extractsChallengeAndSecurityLevel() {
        val challenge = ByteArray(32) { (it + 1).toByte() }
        val info = AttestationParser.parseKeyDescription(keyDescription(challenge, securityLevel = 1))!!
        assertArrayEquals(challenge, info.challenge)
        assertEquals(1, info.securityLevel)
    }

    @Test fun strongBoxLevelParses() {
        val info = AttestationParser.parseKeyDescription(keyDescription(ByteArray(4), securityLevel = 2))!!
        assertEquals(2, info.securityLevel)
    }

    @Test fun malformedReturnsNull() {
        assertNull(AttestationParser.parseKeyDescription(byteArrayOf(0x30, 0x01, 0x02)))
        assertNull(AttestationParser.parseKeyDescription(tlv(0x30, tlv(0x02, byteArrayOf(1))))) // too few fields
    }
}
```

- [ ] **Step 6: Run to verify fail** — expect unresolved `AttestationParser`.

- [ ] **Step 7: Implement `AttestationParser.kt`:**

```kotlin
package tech.ssemaj.pipe.samplehost.data

import java.security.cert.X509Certificate

internal data class AttestationInfo(val challenge: ByteArray, val securityLevel: Int) {
    companion object {
        const val LEVEL_SOFTWARE = 0
        const val LEVEL_TEE = 1
        const val LEVEL_STRONGBOX = 2
    }
}

internal object AttestationParser {
    const val OID = "1.3.6.1.4.1.11129.2.1.17"

    /** Reads the attestation extension from [leaf]; null when absent or malformed. */
    fun parse(leaf: X509Certificate): AttestationInfo? {
        val extension = leaf.getExtensionValue(OID) ?: return null
        // getExtensionValue wraps the payload in an extra OCTET STRING.
        val inner = runCatching { Der.parse(extension, 0) }.getOrNull() ?: return null
        if (inner.tag != 0x04) return null
        return parseKeyDescription(inner.value)
    }

    /** [keyDescriptionDer] is the KeyDescription SEQUENCE (RFC: Android Key Attestation schema). */
    fun parseKeyDescription(keyDescriptionDer: ByteArray): AttestationInfo? = runCatching {
        val seq = Der.parse(keyDescriptionDer, 0)
        if (seq.tag != 0x30) return null
        val fields = Der.children(seq.value)
        if (fields.size < 5) return null
        val securityLevel = fields[1].takeIf { it.tag == 0x0A }?.value?.singleOrNull()?.toInt() ?: return null
        val challenge = fields[4].takeIf { it.tag == 0x04 }?.value ?: return null
        AttestationInfo(challenge, securityLevel)
    }.getOrNull()
}
```

- [ ] **Step 8: Run to verify pass** — `./gradlew :sample-host:testDebugUnitTest --tests "tech.ssemaj.pipe.samplehost.DerTest" --tests "tech.ssemaj.pipe.samplehost.AttestationParserTest"`; expect PASS (6 tests).

- [ ] **Step 9: Commit**

```bash
git add -A && git commit -m "feat: minimal DER + key-attestation extension parser in sample-host"
```

---

### Task 3: `CertificationVerifier` + `VerifyCertificationUseCase` (host)

**Files:**
- Create: `sample-host/src/main/java/tech/ssemaj/pipe/samplehost/data/CertificationVerifier.kt`, `.../domain/CertificationResult.kt`, `.../domain/VerifyCertificationUseCase.kt`
- Test: `sample-host/src/test/java/tech/ssemaj/pipe/samplehost/CertificationVerifierTest.kt`

**Interfaces:**
- Consumes: `AttestationParser.parse(leaf)` (Task 2).
- Produces: `class CertificationVerifier(trustedRootKeysPem: List<String> = GOOGLE_ROOTS) { fun verify(nonce: ByteArray, signature: ByteArray, certChainDer: List<ByteArray>): CertificationResult }`; `data class CertificationResult(signatureOk: Boolean, challengeOk: Boolean?, chainOk: Boolean, hardwareBacked: Boolean, certificates: List<CertSummary>) { val verified: Boolean }`; `data class CertSummary(subject: String, issuer: String, sha256: String)`; `class VerifyCertificationUseCase(verifier) { suspend operator fun invoke(nonce, granted): CertificationResult }`.

- [ ] **Step 1: Fetch the Google attestation root key.** WebFetch `https://developer.android.com/privacy-and-security/security-key-attestation` (section "Root certificate") and copy the published attestation root public key / certificate PEM. If the page yields the root *certificate*, extract just the base64 body; the verifier compares **root public keys**, so store `X509EncodedKeySpec`-compatible public-key PEM bodies. If unreachable, leave `GOOGLE_ROOTS` as an empty list (badge degrades to Software-backed) and revisit in Task 9 Step 6 where the real device chain is available.

- [ ] **Step 2: Write the failing verifier test** at `.../CertificationVerifierTest.kt`, generating a real chain with BouncyCastle (test-only dep, added in Task 1):

```kotlin
package tech.ssemaj.pipe.samplehost

import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.cert.X509Certificate
import java.util.Date
import org.bouncycastle.asn1.DEROctetString
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.cert.X509v3CertificateBuilder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tech.ssemaj.pipe.samplehost.data.AttestationParser
import tech.ssemaj.pipe.samplehost.data.CertificationVerifier

/** Builds root -> leaf chains mirroring what AndroidKeyStore attestation returns. */
class CertificationVerifierTest {

    private fun ecKeyPair(): KeyPair =
        KeyPairGenerator.getInstance("EC").apply { initialize(256) }.generateKeyPair()

    private fun tlv(tag: Int, value: ByteArray): ByteArray =
        byteArrayOf(tag.toByte(), value.size.toByte()) + value

    private fun keyDescription(challenge: ByteArray, level: Int): ByteArray = tlv(0x30,
        tlv(0x02, byteArrayOf(100)) + tlv(0x0A, byteArrayOf(level.toByte())) +
        tlv(0x02, byteArrayOf(100)) + tlv(0x0A, byteArrayOf(level.toByte())) +
        tlv(0x04, challenge) + tlv(0x04, ByteArray(0)) + tlv(0x30, ByteArray(0)) + tlv(0x30, ByteArray(0)))

    private fun cert(
        subject: String, subjectKey: KeyPair, issuer: String, issuerKey: KeyPair,
        attestationExt: ByteArray? = null,
    ): X509Certificate {
        val builder: X509v3CertificateBuilder = JcaX509v3CertificateBuilder(
            X500Name("CN=$issuer"), BigInteger.valueOf(System.nanoTime()),
            Date(System.currentTimeMillis() - 86_400_000), Date(System.currentTimeMillis() + 86_400_000),
            X500Name("CN=$subject"), subjectKey.public,
        )
        if (attestationExt != null) {
            // KeyDescription is a SEQUENCE; addExtension DER-encodes it and adds the OCTET STRING wrapper.
            builder.addExtension(
                ASN1ObjectIdentifier(AttestationParser.OID), false,
                org.bouncycastle.asn1.ASN1Primitive.fromByteArray(attestationExt),
            )
        }
        val signer = JcaContentSignerBuilder("SHA256withECDSA").build(issuerKey.private)
        return JcaX509CertificateConverter().getCertificate(builder.build(signer))
    }

    private fun sign(nonce: ByteArray, key: KeyPair): ByteArray =
        Signature.getInstance("SHA256withECDSA").run { initSign(key.private); update(nonce); sign() }

    @Test fun happyPath_teeChain_verifies() {
        val nonce = ByteArray(32) { it.toByte() }
        val root = ecKeyPair(); val leaf = ecKeyPair()
        val rootCert = cert("Root", root, "Root", root)
        val leafCert = cert("Leaf", leaf, "Root", root, keyDescription(nonce, level = 1))
        val verifier = CertificationVerifier(trustedRootKeysPem = listOf(pem(rootCert)))
        val result = verifier.verify(nonce, sign(nonce, leaf), listOf(leafCert.encoded, rootCert.encoded))
        assertTrue(result.signatureOk); assertEquals(true, result.challengeOk)
        assertTrue(result.chainOk); assertTrue(result.hardwareBacked); assertTrue(result.verified)
        assertEquals(2, result.certificates.size)
    }

    @Test fun wrongChallenge_failsChallengeCheckOnly() {
        val nonce = ByteArray(32); val root = ecKeyPair(); val leaf = ecKeyPair()
        val rootCert = cert("Root", root, "Root", root)
        val leafCert = cert("Leaf", leaf, "Root", root, keyDescription(ByteArray(32) { 9 }, level = 1))
        val result = CertificationVerifier(emptyList())
            .verify(nonce, sign(nonce, leaf), listOf(leafCert.encoded, rootCert.encoded))
        assertTrue(result.signatureOk); assertEquals(false, result.challengeOk); assertFalse(result.verified)
    }

    @Test fun brokenChain_failsChainCheck() {
        val nonce = ByteArray(32); val root = ecKeyPair(); val leaf = ecKeyPair(); val stranger = ecKeyPair()
        val rootCert = cert("Root", root, "Root", root)
        val leafCert = cert("Leaf", leaf, "Stranger", stranger, keyDescription(nonce, level = 1)) // not signed by root
        val result = CertificationVerifier(emptyList())
            .verify(nonce, sign(nonce, leaf), listOf(leafCert.encoded, rootCert.encoded))
        assertFalse(result.chainOk); assertFalse(result.verified)
    }

    @Test fun unknownRoot_degradesToSoftwareNotFailure() {
        val nonce = ByteArray(32); val root = ecKeyPair(); val leaf = ecKeyPair()
        val rootCert = cert("Root", root, "Root", root)
        val leafCert = cert("Leaf", leaf, "Root", root, keyDescription(nonce, level = 1))
        val result = CertificationVerifier(trustedRootKeysPem = emptyList())
            .verify(nonce, sign(nonce, leaf), listOf(leafCert.encoded, rootCert.encoded))
        assertTrue(result.verified); assertFalse(result.hardwareBacked)
    }

    @Test fun noAttestationExtension_challengeNotApplicable() {
        val nonce = ByteArray(32); val root = ecKeyPair(); val leaf = ecKeyPair()
        val rootCert = cert("Root", root, "Root", root)
        val leafCert = cert("Leaf", leaf, "Root", root, attestationExt = null)
        val result = CertificationVerifier(emptyList())
            .verify(nonce, sign(nonce, leaf), listOf(leafCert.encoded, rootCert.encoded))
        assertEquals(null, result.challengeOk); assertTrue(result.verified); assertFalse(result.hardwareBacked)
    }

    @Test fun badSignature_fails() {
        val nonce = ByteArray(32); val root = ecKeyPair(); val leaf = ecKeyPair()
        val rootCert = cert("Root", root, "Root", root)
        val leafCert = cert("Leaf", leaf, "Root", root, keyDescription(nonce, level = 1))
        val result = CertificationVerifier(emptyList())
            .verify(nonce, ByteArray(70), listOf(leafCert.encoded, rootCert.encoded))
        assertFalse(result.signatureOk); assertFalse(result.verified)
    }

    private fun pem(cert: X509Certificate): String =
        java.util.Base64.getEncoder().encodeToString(cert.publicKey.encoded)
}
```

Note: the attestation extension must land in the cert as the raw KeyDescription DER (the same shape `getExtensionValue` returns under one OCTET STRING wrapper, which BC's `addExtension` adds). If `ASN1OctetString.getInstance(attestationExt)` throws for the hand-built bytes, wrap instead with `DEROctetString(attestationExt)` — the assertion target is `AttestationParser.parse(leaf)` returning the challenge; adjust the encoding call until the happy-path test passes, the parser itself must not change.

- [ ] **Step 3: Run to verify fail** — `./gradlew :sample-host:testDebugUnitTest --tests "tech.ssemaj.pipe.samplehost.CertificationVerifierTest"`; expect unresolved `CertificationVerifier`.

- [ ] **Step 4: Implement.** `.../domain/CertificationResult.kt`:

```kotlin
package tech.ssemaj.pipe.samplehost.domain

data class CertSummary(val subject: String, val issuer: String, val sha256: String)

/**
 * Outcome of the four independent checks. [challengeOk] is null when the leaf carries no
 * attestation extension (not applicable — degrades the badge, doesn't fail verification).
 */
data class CertificationResult(
    val signatureOk: Boolean,
    val challengeOk: Boolean?,
    val chainOk: Boolean,
    val hardwareBacked: Boolean,
    val certificates: List<CertSummary>,
) {
    val verified: Boolean get() = signatureOk && chainOk && challengeOk != false
}
```

`.../data/CertificationVerifier.kt`:

```kotlin
package tech.ssemaj.pipe.samplehost.data

import java.io.ByteArrayInputStream
import java.security.MessageDigest
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.Base64
import tech.ssemaj.pipe.samplehost.domain.CertSummary
import tech.ssemaj.pipe.samplehost.domain.CertificationResult

class CertificationVerifier(
    private val trustedRootKeysPem: List<String> = GOOGLE_ROOTS,
) {

    fun verify(nonce: ByteArray, signature: ByteArray, certChainDer: List<ByteArray>): CertificationResult {
        val factory = CertificateFactory.getInstance("X.509")
        val chain = certChainDer.map { factory.generateCertificate(ByteArrayInputStream(it)) as X509Certificate }
        require(chain.isNotEmpty()) { "empty certificate chain" }
        val leaf = chain.first()

        val signatureOk = runCatching {
            Signature.getInstance("SHA256withECDSA").run {
                initVerify(leaf.publicKey); update(nonce); verify(signature)
            }
        }.getOrDefault(false)

        val attestation = AttestationParser.parse(leaf)
        val challengeOk: Boolean? = attestation?.challenge?.contentEquals(nonce)

        val chainOk = runCatching {
            for (i in 0 until chain.size - 1) chain[i].verify(chain[i + 1].publicKey)
            chain.last().verify(chain.last().publicKey) // self-signed root
            true
        }.getOrDefault(false)

        val rootKeyB64 = Base64.getEncoder().encodeToString(chain.last().publicKey.encoded)
        val rootTrusted = trustedRootKeysPem.any { it.replace("\\s".toRegex(), "") == rootKeyB64 }
        val hardwareBacked = chainOk && rootTrusted && attestation != null && challengeOk == true &&
            (attestation.securityLevel == AttestationInfo.LEVEL_TEE ||
                attestation.securityLevel == AttestationInfo.LEVEL_STRONGBOX)

        val digest = MessageDigest.getInstance("SHA-256")
        val summaries = chain.map {
            CertSummary(
                subject = it.subjectX500Principal.name,
                issuer = it.issuerX500Principal.name,
                sha256 = digest.digest(it.encoded).joinToString("") { b -> "%02x".format(b) },
            )
        }
        return CertificationResult(signatureOk, challengeOk, chainOk, hardwareBacked, summaries)
    }

    companion object {
        /** Google hardware-attestation root public keys (base64 of X.509 SubjectPublicKeyInfo). */
        val GOOGLE_ROOTS: List<String> = listOf(
            // Pasted in Task 3 Step 1 from developer.android.com; empty entry list = badge degrades.
        )
    }
}
```

(Replace the `GOOGLE_ROOTS` body with the constant(s) fetched in Step 1; if none could be fetched, leave `emptyList`-equivalent and note it for Task 9 Step 6.)

`.../domain/VerifyCertificationUseCase.kt`:

```kotlin
package tech.ssemaj.pipe.samplehost.domain

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tech.ssemaj.pipe.samples.contract.CertificationResponse
import tech.ssemaj.pipe.samplehost.data.CertificationVerifier

class VerifyCertificationUseCase(private val verifier: CertificationVerifier) {
    suspend operator fun invoke(nonce: ByteArray, granted: CertificationResponse.Granted): CertificationResult =
        withContext(Dispatchers.Default) {
            verifier.verify(nonce, granted.signature, granted.certChainDer)
        }
}
```

- [ ] **Step 5: Run to verify pass** — expect PASS (6 tests). If BC's extension encoding fights back, apply the note under Step 2.

- [ ] **Step 6: Commit**

```bash
git add -A && git commit -m "feat: host-side certification verifier with attestation checks"
```

---

### Task 4: Provider data + domain — `KeystoreRepository`, `IssueCertificationUseCase`, `PanePresenter`

**Files:**
- Create: `sample-provider/src/main/java/tech/ssemaj/pipe/sampleprovider/data/KeystoreRepository.kt`, `.../domain/IssueCertificationUseCase.kt`, `.../pane/PanePresenter.kt`
- Test: `sample-provider/src/test/java/tech/ssemaj/pipe/sampleprovider/PanePresenterTest.kt`, `sample-provider/src/androidTest/java/tech/ssemaj/pipe/sampleprovider/KeystoreRepositoryTest.kt`

**Interfaces:**
- Consumes: contract types (Task 1).
- Produces: `KeystoreRepository { data class Issued(signature: ByteArray, certChainDer: List<ByteArray>, attested: Boolean); fun signWithAttestedKey(challenge: ByteArray): Issued }`; `IssueCertificationUseCase(keystore) { suspend operator fun invoke(nonce: ByteArray): CertificationResponse.Granted }`; `PanePresenter { val state: StateFlow<State>; fun onRequest(CertificationRequest); fun onIssued(SecurityLevel); fun onDeclined(reason: String) }` with sealed `State { Idle; Consent(hostName, nonceFingerprint, nonce); Issued(level); Declined(reason) }`.

- [ ] **Step 1: Write the failing presenter test** at `.../PanePresenterTest.kt`:

```kotlin
package tech.ssemaj.pipe.sampleprovider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tech.ssemaj.pipe.samples.contract.CertificationRequest
import tech.ssemaj.pipe.samples.contract.SecurityLevel
import tech.ssemaj.pipe.sampleprovider.pane.PanePresenter

class PanePresenterTest {

    @Test fun startsIdle() {
        assertEquals(PanePresenter.State.Idle, PanePresenter().state.value)
    }

    @Test fun requestMovesToConsentWithFingerprint() {
        val p = PanePresenter()
        p.onRequest(CertificationRequest(ByteArray(32) { 0x1F }, "Pipe Sample Host"))
        val s = p.state.value as PanePresenter.State.Consent
        assertEquals("Pipe Sample Host", s.hostName)
        assertEquals("1f1f1f1f1f1f1f1f", s.nonceFingerprint) // first 8 bytes, lowercase hex
        assertEquals(32, s.nonce.size)
    }

    @Test fun approveThenIssuedState() {
        val p = PanePresenter()
        p.onRequest(CertificationRequest(ByteArray(32), "h"))
        p.onIssued(SecurityLevel.HARDWARE)
        assertTrue(p.state.value is PanePresenter.State.Issued)
    }

    @Test fun declineCarriesReason() {
        val p = PanePresenter()
        p.onRequest(CertificationRequest(ByteArray(32), "h"))
        p.onDeclined("user said no")
        assertEquals("user said no", (p.state.value as PanePresenter.State.Declined).reason)
    }

    @Test fun secondRequestReentersConsent() {
        val p = PanePresenter()
        p.onRequest(CertificationRequest(ByteArray(32), "h"))
        p.onDeclined("no")
        p.onRequest(CertificationRequest(ByteArray(32) { 2 }, "h2"))
        assertEquals("h2", (p.state.value as PanePresenter.State.Consent).hostName)
    }
}
```

- [ ] **Step 2: Run to verify fail** — `./gradlew :sample-provider:testDebugUnitTest`; expect unresolved `PanePresenter`.

- [ ] **Step 3: Implement `PanePresenter.kt`:**

```kotlin
package tech.ssemaj.pipe.sampleprovider.pane

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import tech.ssemaj.pipe.samples.contract.CertificationRequest
import tech.ssemaj.pipe.samples.contract.SecurityLevel

/** Pure per-pane state machine; the service wires it to views and the issue use case. */
class PanePresenter {
    sealed interface State {
        data object Idle : State
        data class Consent(val hostName: String, val nonceFingerprint: String, val nonce: ByteArray) : State
        data class Issued(val level: SecurityLevel) : State
        data class Declined(val reason: String) : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    fun onRequest(request: CertificationRequest) {
        val fingerprint = request.nonce.take(8).joinToString("") { "%02x".format(it) }
        _state.value = State.Consent(request.hostDisplayName, fingerprint, request.nonce)
    }

    fun onIssued(level: SecurityLevel) {
        _state.value = State.Issued(level)
    }

    fun onDeclined(reason: String) {
        _state.value = State.Declined(reason)
    }
}
```

- [ ] **Step 4: Run to verify pass** — expect PASS (5 tests).

- [ ] **Step 5: Implement `KeystoreRepository.kt`** (instrumented-tested next step; AndroidKeyStore doesn't exist on the JVM):

```kotlin
package tech.ssemaj.pipe.sampleprovider.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.Signature
import java.security.spec.ECGenParameterSpec

/**
 * Generates a throwaway P-256 signing key in AndroidKeyStore with the host's nonce as
 * attestation challenge, signs the nonce, returns the attestation chain, deletes the key.
 * Falls back to an unattested key when the device can't attest ([attested] = false).
 */
class KeystoreRepository {

    data class Issued(val signature: ByteArray, val certChainDer: List<ByteArray>, val attested: Boolean)

    fun signWithAttestedKey(challenge: ByteArray): Issued {
        val alias = "pipe-cert-" + challenge.take(8).joinToString("") { "%02x".format(it) }
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        try {
            val attested = generateKey(alias, challenge)
            val entry = keyStore.getEntry(alias, null) as KeyStore.PrivateKeyEntry
            val signature = Signature.getInstance("SHA256withECDSA").run {
                initSign(entry.privateKey); update(challenge); sign()
            }
            val chain = keyStore.getCertificateChain(alias).map { it.encoded }
            return Issued(signature, chain, attested)
        } finally {
            runCatching { keyStore.deleteEntry(alias) }
        }
    }

    /** Returns true when the key carries an attestation challenge. */
    private fun generateKey(alias: String, challenge: ByteArray): Boolean {
        fun spec(withAttestation: Boolean): KeyGenParameterSpec =
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN)
                .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                .setDigests(KeyProperties.DIGEST_SHA256)
                .apply { if (withAttestation) setAttestationChallenge(challenge) }
                .build()

        val generator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, ANDROID_KEYSTORE)
        return try {
            generator.initialize(spec(withAttestation = true))
            generator.generateKeyPair()
            true
        } catch (e: Exception) { // ProviderException/StrongBoxUnavailable on non-attesting devices
            generator.initialize(spec(withAttestation = false))
            generator.generateKeyPair()
            false
        }
    }

    private companion object { const val ANDROID_KEYSTORE = "AndroidKeyStore" }
}
```

- [ ] **Step 6: Write the instrumented Keystore test** at `sample-provider/src/androidTest/java/tech/ssemaj/pipe/sampleprovider/KeystoreRepositoryTest.kt`:

```kotlin
package tech.ssemaj.pipe.sampleprovider

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.ByteArrayInputStream
import java.security.KeyStore
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import tech.ssemaj.pipe.sampleprovider.data.KeystoreRepository

@RunWith(AndroidJUnit4::class)
class KeystoreRepositoryTest {

    @Test fun signsChallengeAndCleansUpAlias() {
        val challenge = ByteArray(32) { (it * 3).toByte() }
        val issued = KeystoreRepository().signWithAttestedKey(challenge)

        val leaf = CertificateFactory.getInstance("X.509")
            .generateCertificate(ByteArrayInputStream(issued.certChainDer.first())) as X509Certificate
        val ok = Signature.getInstance("SHA256withECDSA").run {
            initVerify(leaf.publicKey); update(challenge); verify(issued.signature)
        }
        assertTrue("signature must verify against leaf key", ok)
        assertTrue("chain must have at least leaf", issued.certChainDer.isNotEmpty())

        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        assertFalse("alias must be deleted", ks.aliases().toList().any { it.startsWith("pipe-cert-") })
    }
}
```

- [ ] **Step 7: Implement `IssueCertificationUseCase.kt`:**

```kotlin
package tech.ssemaj.pipe.sampleprovider.domain

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tech.ssemaj.pipe.samples.contract.CertificationResponse
import tech.ssemaj.pipe.samples.contract.SecurityLevel
import tech.ssemaj.pipe.sampleprovider.data.KeystoreRepository

class IssueCertificationUseCase(private val keystore: KeystoreRepository) {
    suspend operator fun invoke(nonce: ByteArray): CertificationResponse.Granted =
        withContext(Dispatchers.IO) {
            val issued = keystore.signWithAttestedKey(nonce)
            CertificationResponse.Granted(
                signature = issued.signature,
                certChainDer = issued.certChainDer,
                securityLevel = if (issued.attested) SecurityLevel.HARDWARE else SecurityLevel.SOFTWARE,
            )
        }
}
```

- [ ] **Step 8: Build + run the JVM test; run the instrumented test on the device** —
`./gradlew :sample-provider:testDebugUnitTest :sample-provider:assembleDebug` (PASS), then wake the device (`adb -s 19011FDEE0040L shell input keyevent KEYCODE_WAKEUP && adb -s 19011FDEE0040L shell wm dismiss-keyguard`) and `ANDROID_SERIAL=19011FDEE0040L ./gradlew :sample-provider:connectedDebugAndroidTest`; expect 1/1 PASS.

- [ ] **Step 9: Commit**

```bash
git add -A && git commit -m "feat: provider keystore attestation data layer and pane presenter"
```

---

### Task 5: Provider pane UI + `DemoPaneService` rewrite

**Files:**
- Modify: `sample-provider/src/main/java/tech/ssemaj/pipe/sampleprovider/DemoPaneService.kt`
- Create: `sample-provider/src/main/res/values/themes.xml`

**Interfaces:**
- Consumes: `PanePresenter`, `IssueCertificationUseCase`, `KeystoreRepository` (Task 4); `PipeCodec.decodeOrNull<CertificationRequest>`, `HostHandle.send<CertificationResponse>(...)` (`:pipe-serialization`); `paneScope` from `PipeProviderService`.
- Produces: pane UI strings the e2e suite reads — idle caption exactly `pane-ready`, consent title `"<hostName> requests a device certification"`, buttons `Approve` / `Decline`, issued text `Certification issued`, declined text `Certification declined`.

- [ ] **Step 1: Provider Material theme** — `sample-provider/src/main/res/values/themes.xml`:

```xml
<resources>
    <style name="Theme.PipeProvider" parent="Theme.Material3.DayNight.NoActionBar">
        <item name="colorPrimary">#00696D</item>
        <item name="colorOnPrimary">#FFFFFF</item>
    </style>
</resources>
```

- [ ] **Step 2: Rewrite `DemoPaneService.kt`.** Views are built in code (no inflation — one file, per-pane state) against a `ContextThemeWrapper` so Material widgets resolve theme attributes. Each `onOpenPane` call gets its OWN presenter (multi-pane safe):

```kotlin
package tech.ssemaj.pipe.sampleprovider

import android.graphics.Color
import android.graphics.Typeface
import android.util.TypedValue
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch
import tech.ssemaj.pipe.core.PipeMessage
import tech.ssemaj.pipe.core.PipeRequest
import tech.ssemaj.pipe.provider.HostHandle
import tech.ssemaj.pipe.provider.PaneResult
import tech.ssemaj.pipe.provider.PipeContent
import tech.ssemaj.pipe.provider.PipeProviderService
import tech.ssemaj.pipe.samples.contract.CertificationRequest
import tech.ssemaj.pipe.samples.contract.CertificationResponse
import tech.ssemaj.pipe.sampleprovider.data.KeystoreRepository
import tech.ssemaj.pipe.sampleprovider.domain.IssueCertificationUseCase
import tech.ssemaj.pipe.sampleprovider.pane.PanePresenter
import tech.ssemaj.pipe.serialization.PipeCodec
import tech.ssemaj.pipe.serialization.send

class DemoPaneService : PipeProviderService() {

    private val issueCertification = IssueCertificationUseCase(KeystoreRepository())

    override suspend fun onOpenPane(request: PipeRequest, host: HostHandle): PaneResult {
        val themed = ContextThemeWrapper(this, R.style.Theme_PipeProvider)
        val presenter = PanePresenter()

        fun text(value: String, sizeSp: Float = 14f, bold: Boolean = false) = TextView(themed).apply {
            this.text = value
            setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
            setTextColor(Color.parseColor("#1C1B1F"))
            if (bold) setTypeface(typeface, Typeface.BOLD)
        }

        val title = text("Pipe Certification Provider", 16f, bold = true)
        val caption = text("pane-ready")
        val consentTitle = text("", 15f, bold = true)
        val consentFingerprint = text("", 12f)
        val approve = MaterialButton(themed).apply { text = "Approve" }
        val decline = MaterialButton(
            themed, null, com.google.android.material.R.attr.materialButtonOutlinedStyle,
        ).apply { text = "Decline" }
        val buttons = LinearLayout(themed).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(decline, LinearLayout.LayoutParams(0, WRAP, 1f))
            addView(approve, LinearLayout.LayoutParams(0, WRAP, 1f).apply { marginStart = 16 })
        }
        val consentGroup = LinearLayout(themed).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            addView(consentTitle); addView(consentFingerprint); addView(buttons)
        }
        val resultText = text("", 15f, bold = true).apply { visibility = View.GONE }
        val root = LinearLayout(themed).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(Color.WHITE)
            setPadding(32, 32, 32, 32)
            addView(title); addView(caption); addView(consentGroup); addView(resultText)
        }

        // Render presenter state (main thread — paneScope is Main).
        paneScope.launch {
            presenter.state.collect { state ->
                consentGroup.visibility = if (state is PanePresenter.State.Consent) View.VISIBLE else View.GONE
                resultText.visibility =
                    if (state is PanePresenter.State.Issued || state is PanePresenter.State.Declined) View.VISIBLE
                    else View.GONE
                when (state) {
                    PanePresenter.State.Idle -> caption.text = "pane-ready"
                    is PanePresenter.State.Consent -> {
                        caption.text = "consent required"
                        consentTitle.text = "${state.hostName} requests a device certification"
                        consentFingerprint.text = "challenge ${state.nonceFingerprint}"
                    }
                    is PanePresenter.State.Issued -> resultText.text = "Certification issued"
                    is PanePresenter.State.Declined -> resultText.text = "Certification declined"
                }
            }
        }

        approve.setOnClickListener {
            val consent = presenter.state.value as? PanePresenter.State.Consent ?: return@setOnClickListener
            paneScope.launch {
                val granted = issueCertification(consent.nonce)
                host.send<CertificationResponse>(granted)
                presenter.onIssued(granted.securityLevel)
            }
        }
        decline.setOnClickListener {
            paneScope.launch {
                host.send<CertificationResponse>(CertificationResponse.Declined("user declined"))
                presenter.onDeclined("user declined")
            }
        }

        return PaneResult.Content(object : PipeContent {
            override val view: View = root
            override fun onMessage(message: PipeMessage) {
                PipeCodec.decodeOrNull<CertificationRequest>(message)?.let(presenter::onRequest)
            }
        })
    }

    private companion object { const val WRAP = LinearLayout.LayoutParams.WRAP_CONTENT }
}
```

- [ ] **Step 3: Assemble** — `./gradlew :sample-provider:assembleDebug`; BUILD SUCCESSFUL. (The old `PipeE2eTest` strings like `Ping Host` are gone — the host+tests are rewritten in Tasks 6–9; connected suites are not run until then.)

- [ ] **Step 4: Commit**

```bash
git add -A && git commit -m "feat: provider certification consent pane with Material styling"
```

---

### Task 6: Host data + domain — `PipeSessionRepository`, `RequestCertificationUseCase`

**Files:**
- Create: `sample-host/src/main/java/tech/ssemaj/pipe/samplehost/data/PipeSessionRepository.kt`, `.../domain/RequestCertificationUseCase.kt`
- Test: `sample-host/src/test/java/tech/ssemaj/pipe/samplehost/RequestCertificationUseCaseTest.kt`

**Interfaces:**
- Consumes: `PipeSession` (`state: StateFlow<PipeState>`, `messages: Flow<PipeMessage>`, `suspend send`, `close()`), `PipeView.open(provider, request, authorizer)`, typed extensions `PipeSession.send<T>` / `messagesOf<T>`.
- Produces: `PipeSessionRepository { val session: StateFlow<PipeSession?>; suspend fun open(view: PipeView, provider: ProviderComponent, authorizer: PipeAuthorizer): PipeSession; fun close() }`; `RequestCertificationUseCase(nonceSource) { suspend operator fun invoke(session, hostDisplayName): Outcome }` with sealed `Outcome { NeedsVerification(nonce, granted); Declined(reason); Timeout }` and `RESPONSE_TIMEOUT_MS = 15_000L`.

- [ ] **Step 1: Write the failing use-case test** at `.../RequestCertificationUseCaseTest.kt` with a fake session:

```kotlin
package tech.ssemaj.pipe.samplehost

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tech.ssemaj.pipe.auth.PeerIdentity
import tech.ssemaj.pipe.core.PipeMessage
import tech.ssemaj.pipe.core.PipeSize
import tech.ssemaj.pipe.core.PipeState
import tech.ssemaj.pipe.host.PipeSession
import tech.ssemaj.pipe.samples.contract.CertificationRequest
import tech.ssemaj.pipe.samples.contract.CertificationResponse
import tech.ssemaj.pipe.samples.contract.SecurityLevel
import tech.ssemaj.pipe.samplehost.domain.RequestCertificationUseCase
import tech.ssemaj.pipe.serialization.PipeCodec

private class FakeSession : PipeSession {
    val sent = mutableListOf<PipeMessage>()
    val incoming = Channel<PipeMessage>(Channel.UNLIMITED)
    override val peer = PeerIdentity(uid = 10_001, packages = listOf("fake"), signingCertSha256 = emptyList())
    override val state: StateFlow<PipeState> = MutableStateFlow<PipeState>(PipeState.Open(peer))
    override val messages: Flow<PipeMessage> = incoming.receiveAsFlow()
    override suspend fun send(message: PipeMessage): Boolean { sent.add(message); return true }
    override suspend fun resize(size: PipeSize) {}
    override fun close() {}
}

class RequestCertificationUseCaseTest {

    private val useCase = RequestCertificationUseCase(nonceSource = { ByteArray(32) { 7 } })

    @Test fun sendsRequestAndReturnsGrantedForVerification() = runTest {
        val session = FakeSession()
        launch {
            // Echo the provider: wait for the request, reply Granted.
            while (session.sent.isEmpty()) kotlinx.coroutines.yield()
            val request = PipeCodec.decodeOrNull<CertificationRequest>(session.sent.first())!!
            assertEquals("Test Host", request.hostDisplayName)
            val reply: CertificationResponse = CertificationResponse.Granted(
                signature = byteArrayOf(9), certChainDer = listOf(byteArrayOf(1)),
                securityLevel = SecurityLevel.HARDWARE,
            )
            session.incoming.send(PipeCodec.encode(reply))
        }
        val outcome = useCase(session, hostDisplayName = "Test Host")
        val needs = outcome as RequestCertificationUseCase.Outcome.NeedsVerification
        assertArrayEquals(ByteArray(32) { 7 }, needs.nonce)
        assertArrayEquals(byteArrayOf(9), needs.granted.signature)
    }

    @Test fun declinedPassesReasonThrough() = runTest {
        val session = FakeSession()
        launch {
            while (session.sent.isEmpty()) kotlinx.coroutines.yield()
            val reply: CertificationResponse = CertificationResponse.Declined("nope")
            session.incoming.send(PipeCodec.encode(reply))
        }
        val outcome = useCase(session, "Test Host")
        assertEquals("nope", (outcome as RequestCertificationUseCase.Outcome.Declined).reason)
    }

    @Test fun timesOutWhenProviderSilent() = runTest {
        val outcome = useCase(FakeSession(), "Test Host") // virtual time: withTimeout fires instantly under runTest
        assertTrue(outcome is RequestCertificationUseCase.Outcome.Timeout)
    }
}
```

Note: `PipeCodec` uses `Bundle` — these are JVM unit tests, so `sample-host/build.gradle.kts` needs `testOptions { unitTests { isReturnDefaultValues = true; isIncludeAndroidResources = true } }` and `testImplementation(libs.robolectric)` ONLY IF `Bundle` operations fail on the JVM. Try plain first; if `Bundle.putByteArray` throws `not mocked`, annotate the test class with `@RunWith(org.robolectric.RobolectricTestRunner::class)` and add the robolectric dep (catalog alias `robolectric` already exists).

- [ ] **Step 2: Run to verify fail** — unresolved `RequestCertificationUseCase`.

- [ ] **Step 3: Implement `RequestCertificationUseCase.kt`:**

```kotlin
package tech.ssemaj.pipe.samplehost.domain

import java.security.SecureRandom
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import tech.ssemaj.pipe.host.PipeSession
import tech.ssemaj.pipe.samples.contract.CertificationRequest
import tech.ssemaj.pipe.samples.contract.CertificationResponse
import tech.ssemaj.pipe.serialization.messagesOf
import tech.ssemaj.pipe.serialization.send

class RequestCertificationUseCase(
    private val nonceSource: () -> ByteArray = { ByteArray(32).also { SecureRandom().nextBytes(it) } },
) {
    sealed interface Outcome {
        data class NeedsVerification(val nonce: ByteArray, val granted: CertificationResponse.Granted) : Outcome
        data class Declined(val reason: String) : Outcome
        data object Timeout : Outcome
    }

    suspend operator fun invoke(session: PipeSession, hostDisplayName: String): Outcome {
        val nonce = nonceSource()
        session.send(CertificationRequest(nonce, hostDisplayName))
        val response = withTimeoutOrNull(RESPONSE_TIMEOUT_MS) {
            session.messagesOf<CertificationResponse>().first()
        } ?: return Outcome.Timeout
        return when (response) {
            is CertificationResponse.Granted -> Outcome.NeedsVerification(nonce, response)
            is CertificationResponse.Declined -> Outcome.Declined(response.reason)
        }
    }

    companion object { const val RESPONSE_TIMEOUT_MS = 15_000L }
}
```

- [ ] **Step 4: Implement `PipeSessionRepository.kt`:**

```kotlin
package tech.ssemaj.pipe.samplehost.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import tech.ssemaj.pipe.auth.PipeAuthorizer
import tech.ssemaj.pipe.core.PipeRequest
import tech.ssemaj.pipe.host.PipeSession
import tech.ssemaj.pipe.host.PipeView
import tech.ssemaj.pipe.host.ProviderComponent
import tech.ssemaj.pipe.samples.contract.ACTION_CERTIFICATION

/** Owns the live session; callers pass the PipeView at open time (never retained here). */
class PipeSessionRepository {
    private val _session = MutableStateFlow<PipeSession?>(null)
    val session: StateFlow<PipeSession?> = _session.asStateFlow()

    suspend fun open(view: PipeView, provider: ProviderComponent, authorizer: PipeAuthorizer): PipeSession {
        val s = view.open(provider, PipeRequest(ACTION_CERTIFICATION), authorizer)
        _session.value = s
        return s
    }

    fun close() {
        _session.value?.close()
        _session.value = null
    }
}
```

- [ ] **Step 5: Run to verify pass** — `./gradlew :sample-host:testDebugUnitTest --tests "tech.ssemaj.pipe.samplehost.RequestCertificationUseCaseTest"`; expect PASS (3 tests; apply the Robolectric note from Step 1 if `Bundle` is not mocked).

- [ ] **Step 6: Commit**

```bash
git add -A && git commit -m "feat: host session repository and certification request use case"
```

---

### Task 7: Host presentation — theme, ViewModel, Compose screen, `MainActivity` rewrite

**Files:**
- Create: `.../samplehost/di/AppContainer.kt`, `.../presentation/UiState.kt`, `.../presentation/CertificationViewModel.kt`, `.../presentation/ui/Theme.kt`, `.../presentation/ui/CertificationScreen.kt`, `sample-host/src/main/res/values/themes.xml`
- Modify: `sample-host/src/main/java/tech/ssemaj/pipe/samplehost/MainActivity.kt`, `sample-host/src/main/AndroidManifest.xml`
- Delete: `sample-host/src/main/res/layout/activity_main.xml`

**Interfaces:**
- Consumes: Tasks 3 & 6 use cases/repos; `PipeView`; contract.
- Produces: UI strings the e2e reads — chip `Connecting…`/`Connected`/`Closed`, button `Request certification`, badge `Hardware-verified`/`Software-backed`/`Verification failed`, `Provider declined: <reason>`, `No response from provider`, `denied: <reason>`, `error: <SimpleName>`, `Provider disconnected`, app-bar action contentDescription `Reopen`.

- [ ] **Step 1: `UiState.kt`:**

```kotlin
package tech.ssemaj.pipe.samplehost.presentation

import tech.ssemaj.pipe.samplehost.domain.CertificationResult

enum class PipeStatus { CONNECTING, CONNECTED, CLOSED }

sealed interface FlowPhase {
    data object Idle : FlowPhase
    data object WaitingForProvider : FlowPhase
    data object Verifying : FlowPhase
    data class Done(val result: CertificationResult) : FlowPhase
    data class Declined(val reason: String) : FlowPhase
    data object Timeout : FlowPhase
    /** Pipe-level failure; [text] is user-visible and keeps the `denied: ` prefix for tests. */
    data class PipeFailure(val text: String) : FlowPhase
}

data class UiState(
    val pipeStatus: PipeStatus = PipeStatus.CONNECTING,
    val phase: FlowPhase = FlowPhase.Idle,
    /** Incremented on Reopen; keys the AndroidView so a fresh PipeView is created. */
    val paneGeneration: Int = 0,
)
```

- [ ] **Step 2: `AppContainer.kt`:**

```kotlin
package tech.ssemaj.pipe.samplehost.di

import tech.ssemaj.pipe.samplehost.data.CertificationVerifier
import tech.ssemaj.pipe.samplehost.data.PipeSessionRepository
import tech.ssemaj.pipe.samplehost.domain.RequestCertificationUseCase
import tech.ssemaj.pipe.samplehost.domain.VerifyCertificationUseCase

/** Hand-rolled DI: one instance per ViewModel. */
class AppContainer {
    val sessionRepository = PipeSessionRepository()
    val requestCertification = RequestCertificationUseCase()
    val verifyCertification = VerifyCertificationUseCase(CertificationVerifier())
}
```

- [ ] **Step 3: `CertificationViewModel.kt`:**

```kotlin
package tech.ssemaj.pipe.samplehost.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tech.ssemaj.pipe.auth.PipeAuthorizer
import tech.ssemaj.pipe.core.PipeDeniedException
import tech.ssemaj.pipe.core.PipeException
import tech.ssemaj.pipe.core.PipeState
import tech.ssemaj.pipe.host.PipeView
import tech.ssemaj.pipe.host.ProviderComponent
import tech.ssemaj.pipe.samplehost.di.AppContainer
import tech.ssemaj.pipe.samplehost.domain.RequestCertificationUseCase

class CertificationViewModel(
    private val container: AppContainer = AppContainer(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var stateWatcher: Job? = null

    /** Called from the AndroidView factory each time a pane view is (re)created. */
    fun onPaneViewReady(view: PipeView, provider: ProviderComponent, authorizer: PipeAuthorizer) {
        _uiState.update { it.copy(pipeStatus = PipeStatus.CONNECTING, phase = FlowPhase.Idle) }
        viewModelScope.launch {
            try {
                val session = container.sessionRepository.open(view, provider, authorizer)
                _uiState.update { it.copy(pipeStatus = PipeStatus.CONNECTED) }
                stateWatcher?.cancel()
                stateWatcher = launch {
                    session.state.collect { s ->
                        if (s is PipeState.Closed) {
                            _uiState.update { it.copy(pipeStatus = PipeStatus.CLOSED) }
                            if (s.cause != null) {
                                _uiState.update { it.copy(phase = FlowPhase.PipeFailure("Provider disconnected")) }
                            }
                        }
                    }
                }
            } catch (e: PipeDeniedException) {
                _uiState.update {
                    it.copy(pipeStatus = PipeStatus.CLOSED, phase = FlowPhase.PipeFailure("denied: ${e.reason}"))
                }
            } catch (e: PipeException) {
                _uiState.update {
                    it.copy(pipeStatus = PipeStatus.CLOSED, phase = FlowPhase.PipeFailure("error: ${e::class.simpleName}"))
                }
            }
        }
    }

    fun requestCertification(hostDisplayName: String) {
        val session = container.sessionRepository.session.value ?: return
        _uiState.update { it.copy(phase = FlowPhase.WaitingForProvider) }
        viewModelScope.launch {
            when (val outcome = container.requestCertification(session, hostDisplayName)) {
                is RequestCertificationUseCase.Outcome.NeedsVerification -> {
                    _uiState.update { it.copy(phase = FlowPhase.Verifying) }
                    val result = container.verifyCertification(outcome.nonce, outcome.granted)
                    _uiState.update { it.copy(phase = FlowPhase.Done(result)) }
                }
                is RequestCertificationUseCase.Outcome.Declined ->
                    _uiState.update { it.copy(phase = FlowPhase.Declined(outcome.reason)) }
                RequestCertificationUseCase.Outcome.Timeout ->
                    _uiState.update { it.copy(phase = FlowPhase.Timeout) }
            }
        }
    }

    fun reopen() {
        stateWatcher?.cancel()
        container.sessionRepository.close()
        _uiState.update {
            it.copy(pipeStatus = PipeStatus.CONNECTING, phase = FlowPhase.Idle, paneGeneration = it.paneGeneration + 1)
        }
    }

    override fun onCleared() {
        container.sessionRepository.close()
    }
}
```

- [ ] **Step 4: `Theme.kt`:**

```kotlin
package tech.ssemaj.pipe.samplehost.presentation.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

@Composable
fun PipeDemoTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val colorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}
```

- [ ] **Step 5: `CertificationScreen.kt`** — the full screen; pane card hosts the `PipeView` via `AndroidView`, keyed by `paneGeneration`:

```kotlin
package tech.ssemaj.pipe.samplehost.presentation.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import tech.ssemaj.pipe.host.PipeView
import tech.ssemaj.pipe.samplehost.domain.CertificationResult
import tech.ssemaj.pipe.samplehost.presentation.FlowPhase
import tech.ssemaj.pipe.samplehost.presentation.PipeStatus
import tech.ssemaj.pipe.samplehost.presentation.UiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CertificationScreen(
    state: UiState,
    onPaneViewCreated: (PipeView) -> Unit,
    onRequest: () -> Unit,
    onReopen: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pipe Certification") },
                actions = {
                    StatusChip(state.pipeStatus)
                    TextButton(onClick = onReopen) { Text("Reopen") }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                key(state.paneGeneration) {
                    AndroidView(
                        factory = { context -> PipeView(context).also(onPaneViewCreated) },
                        modifier = Modifier.fillMaxWidth().height(260.dp),
                    )
                }
            }
            Button(
                onClick = onRequest,
                enabled = state.pipeStatus == PipeStatus.CONNECTED &&
                    state.phase !is FlowPhase.WaitingForProvider && state.phase !is FlowPhase.Verifying,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Request certification") }
            PhaseCard(state.phase)
        }
    }
}

@Composable
private fun StatusChip(status: PipeStatus) {
    AssistChip(onClick = {}, label = {
        Text(
            when (status) {
                PipeStatus.CONNECTING -> "Connecting…"
                PipeStatus.CONNECTED -> "Connected"
                PipeStatus.CLOSED -> "Closed"
            }
        )
    })
}

@Composable
private fun PhaseCard(phase: FlowPhase) {
    when (phase) {
        FlowPhase.Idle -> {}
        FlowPhase.WaitingForProvider -> ProgressRow("Challenge sent — waiting for provider approval")
        FlowPhase.Verifying -> ProgressRow("Verifying certification")
        is FlowPhase.Done -> ResultCard(phase.result)
        is FlowPhase.Declined -> MessageCard("Provider declined: ${phase.reason}")
        FlowPhase.Timeout -> MessageCard("No response from provider")
        is FlowPhase.PipeFailure -> MessageCard(phase.text, isError = true)
    }
}

@Composable
private fun ProgressRow(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(modifier = Modifier.width(24.dp).height(24.dp))
        Spacer(Modifier.width(12.dp))
        Text(text)
    }
}

@Composable
private fun MessageCard(text: String, isError: Boolean = false) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Text(
            text,
            modifier = Modifier.padding(16.dp),
            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun ResultCard(result: CertificationResult) {
    var expanded by remember { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                when {
                    !result.verified -> "Verification failed"
                    result.hardwareBacked -> "Hardware-verified"
                    else -> "Software-backed"
                },
                style = MaterialTheme.typography.titleLarge,
                color = if (result.verified) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
            Text("signature ${if (result.signatureOk) "ok" else "FAILED"} · " +
                "challenge ${result.challengeOk?.let { if (it) "ok" else "FAILED" } ?: "n/a"} · " +
                "chain ${if (result.chainOk) "ok" else "FAILED"}")
            TextButton(onClick = { expanded = !expanded }) {
                Text(if (expanded) "Hide certificate chain" else "Show certificate chain")
            }
            if (expanded) {
                result.certificates.forEach { cert ->
                    Column {
                        Text(cert.subject, style = MaterialTheme.typography.bodyMedium)
                        Text("issuer: ${cert.issuer}", style = MaterialTheme.typography.bodySmall)
                        Text("sha256: ${cert.sha256.take(32)}…", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
```

(The `Reopen` control is a `TextButton` labelled `Reopen` — UiAutomator finds it with `By.text("Reopen")`. Remove the unused `Icon`/`IconButton`/`ImageVector` imports if the compiler flags them.)

- [ ] **Step 6: Rewrite `MainActivity.kt`** (keeps the security-suite extras contract):

```kotlin
package tech.ssemaj.pipe.samplehost

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import kotlinx.coroutines.yield
import tech.ssemaj.pipe.auth.AuthDecision
import tech.ssemaj.pipe.auth.PipeAuthorizer
import tech.ssemaj.pipe.auth.PipeAuthorizers
import tech.ssemaj.pipe.host.ProviderComponent
import tech.ssemaj.pipe.samplehost.presentation.CertificationViewModel
import tech.ssemaj.pipe.samplehost.presentation.ui.CertificationScreen
import tech.ssemaj.pipe.samplehost.presentation.ui.PipeDemoTheme

class MainActivity : ComponentActivity() {

    private val viewModel: CertificationViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val provider = ProviderComponent(
            packageName = intent.getStringExtra("targetPackage") ?: "tech.ssemaj.pipe.sampleprovider",
            serviceClass = intent.getStringExtra("targetService") ?: "tech.ssemaj.pipe.sampleprovider.DemoPaneService",
        )
        val authorizer: PipeAuthorizer = when (intent.getStringExtra("targetAuthorizer")) {
            "suspend-deny" -> PipeAuthorizer { _, _ ->
                yield()
                AuthDecision.Deny("async-policy")
            }
            else -> PipeAuthorizers.sameSigningKey(this)
        }
        setContent {
            PipeDemoTheme {
                val state by viewModel.uiState.collectAsState()
                CertificationScreen(
                    state = state,
                    onPaneViewCreated = { view -> viewModel.onPaneViewReady(view, provider, authorizer) },
                    onRequest = { viewModel.requestCertification(hostDisplayName = "Pipe Sample Host") },
                    onReopen = viewModel::reopen,
                )
            }
        }
    }
}
```

- [ ] **Step 7: Theme + manifest.** `sample-host/src/main/res/values/themes.xml`:

```xml
<resources>
    <style name="Theme.PipeHost" parent="android:Theme.Material.Light.NoActionBar" />
</resources>
```

In `sample-host/src/main/AndroidManifest.xml` set `android:theme="@style/Theme.PipeHost"` on `<application>`. Delete `sample-host/src/main/res/layout/activity_main.xml`. Remove the appcompat dependency ONLY if nothing else uses it (`MultiPaneActivity` in Task 8 uses plain `android.app.Activity`, so `implementation(libs.appcompat)` can be dropped from sample-host).

- [ ] **Step 8: Assemble + unit tests** — `./gradlew :sample-host:assembleDebug :sample-host:testDebugUnitTest`; BUILD SUCCESSFUL, all unit tests PASS.

- [ ] **Step 9: Manual smoke on device** — `ANDROID_SERIAL=19011FDEE0040L ./gradlew :sample-host:installDebug :sample-provider:installDebug`, launch `adb shell am start -n tech.ssemaj.pipe.samplehost/.MainActivity`, then screenshot: `adb exec-out screencap -p > scratchpad/host-smoke.png` and eyeball: pane card shows `pane-ready`, tap Request certification → consent appears → Approve → badge appears.

- [ ] **Step 10: Commit**

```bash
git add -A && git commit -m "feat: Compose certification host UI with MVVM"
```

---

### Task 8: `MultiPaneActivity` (plain XML, two panes)

**Files:**
- Create: `sample-host/src/main/java/tech/ssemaj/pipe/samplehost/MultiPaneActivity.kt`, `sample-host/src/main/res/layout/activity_multi_pane.xml`
- Modify: `sample-host/src/main/AndroidManifest.xml`

**Interfaces:**
- Consumes: `PipeView.open`, typed messaging, contract.
- Produces: activity `tech.ssemaj.pipe.samplehost.MultiPaneActivity` (exported false, launched by test via explicit intent); status texts `A: verified` / `B: verified` (or `A: <failure>` on any non-granted outcome).

- [ ] **Step 1: Layout** `activity_multi_pane.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:orientation="vertical"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:padding="16dp">
    <TextView android:id="@+id/status_a" android:text="A: opening"
        android:layout_width="wrap_content" android:layout_height="wrap_content" />
    <tech.ssemaj.pipe.host.PipeView android:id="@+id/pane_a"
        android:layout_width="match_parent" android:layout_height="0dp" android:layout_weight="1" />
    <TextView android:id="@+id/status_b" android:text="B: opening"
        android:layout_width="wrap_content" android:layout_height="wrap_content" />
    <tech.ssemaj.pipe.host.PipeView android:id="@+id/pane_b"
        android:layout_width="match_parent" android:layout_height="0dp" android:layout_weight="1" />
</LinearLayout>
```

- [ ] **Step 2: `MultiPaneActivity.kt`** — both panes auto-request on open; deliberately bare-bones (exists for the e2e):

```kotlin
package tech.ssemaj.pipe.samplehost

import android.app.Activity
import android.os.Bundle
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import tech.ssemaj.pipe.core.PipeException
import tech.ssemaj.pipe.core.PipeRequest
import tech.ssemaj.pipe.host.PipeView
import tech.ssemaj.pipe.host.ProviderComponent
import tech.ssemaj.pipe.samples.contract.ACTION_CERTIFICATION
import tech.ssemaj.pipe.samplehost.di.AppContainer
import tech.ssemaj.pipe.samplehost.domain.RequestCertificationUseCase

/** Two panes on one provider — exists for MultiPaneE2eTest, intentionally plain. */
class MultiPaneActivity : Activity() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val container = AppContainer()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_multi_pane)
        val provider = ProviderComponent(
            packageName = "tech.ssemaj.pipe.sampleprovider",
            serviceClass = "tech.ssemaj.pipe.sampleprovider.DemoPaneService",
        )
        runPane(findViewById(R.id.pane_a), findViewById(R.id.status_a), "A", provider)
        runPane(findViewById(R.id.pane_b), findViewById(R.id.status_b), "B", provider)
    }

    private fun runPane(pane: PipeView, status: TextView, label: String, provider: ProviderComponent) {
        scope.launch {
            try {
                val session = pane.open(provider, PipeRequest(ACTION_CERTIFICATION))
                status.text = "$label: connected"
                when (val outcome = container.requestCertification(session, "Multi-Pane Host $label")) {
                    is RequestCertificationUseCase.Outcome.NeedsVerification -> {
                        val result = container.verifyCertification(outcome.nonce, outcome.granted)
                        status.text = if (result.verified) "$label: verified" else "$label: verification failed"
                    }
                    is RequestCertificationUseCase.Outcome.Declined -> status.text = "$label: declined"
                    RequestCertificationUseCase.Outcome.Timeout -> status.text = "$label: timeout"
                }
            } catch (e: PipeException) {
                status.text = "$label: error ${e::class.simpleName}"
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
```

- [ ] **Step 3: Manifest** — inside `<application>` add:

```xml
<activity android:name=".MultiPaneActivity" android:exported="false" />
```

- [ ] **Step 4: Assemble** — `./gradlew :sample-host:assembleDebug`; BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat: multi-pane demo activity"
```

---

### Task 9: Instrumented suite — port `PipeE2eTest`, add `MultiPaneE2eTest` + `ReopenE2eTest`, device gate

**Files:**
- Rewrite: `sample-host/src/androidTest/java/tech/ssemaj/pipe/samplehost/PipeE2eTest.kt`
- Create: `.../MultiPaneE2eTest.kt`, `.../ReopenE2eTest.kt`
- Verify unchanged: `SecurityE2eTest.kt`, `SuspendAuthorizerDenialTest.kt`, `evil-host/.../EvilHostDeniedTest.kt`

**Interfaces:**
- Consumes: every UI string produced in Tasks 5, 7, 8.

- [ ] **Step 1: Rewrite `PipeE2eTest.kt`:**

```kotlin
package tech.ssemaj.pipe.samplehost

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

private const val TIMEOUT = 10_000L
private const val CRYPTO_TIMEOUT = 20_000L // key attestation generation can be slow

/**
 * Requires BOTH apps installed. The embedded pane is a real window of the provider
 * process, so UiAutomator is used for pane-side assertions.
 */
@RunWith(AndroidJUnit4::class)
class PipeE2eTest {
    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    @Before fun launch() {
        ActivityScenario.launch(MainActivity::class.java)
        assertTrue("pane did not render", device.wait(Until.hasObject(By.text("pane-ready")), TIMEOUT))
    }

    @Test fun paneRendersAndHostReportsConnected() {
        assertNotNull(device.wait(Until.findObject(By.text("Connected")), TIMEOUT))
    }

    @Test fun certificationRoundTrip_touchAndBothChannels() {
        device.findObject(By.text("Request certification")).click()
        // Host→provider typed message arrived: consent UI appears in the pane.
        assertTrue("consent never appeared in pane",
            device.wait(Until.hasObject(By.text("Approve")), TIMEOUT))
        device.findObject(By.text("Approve")).click() // touch crosses into the provider window
        // Provider→host: granted response arrives, host verifies.
        assertTrue("host never showed a verification badge",
            device.wait(Until.hasObject(By.textContains("-verified")), CRYPTO_TIMEOUT) ||
                device.hasObject(By.text("Software-backed")))
        assertTrue("pane never confirmed issuance",
            device.wait(Until.hasObject(By.text("Certification issued")), TIMEOUT))
    }

    @Test fun declineFlowReachesHost() {
        device.findObject(By.text("Request certification")).click()
        assertTrue(device.wait(Until.hasObject(By.text("Decline")), TIMEOUT))
        device.findObject(By.text("Decline")).click()
        assertTrue("host never showed decline",
            device.wait(Until.hasObject(By.textStartsWith("Provider declined:")), TIMEOUT))
    }

    @Test fun closingHostActivityTearsDownPane() {
        device.pressHome()
        assertTrue("pane still visible after host gone",
            device.wait(Until.gone(By.text("pane-ready")), TIMEOUT))
    }
}
```

(The badge assertion accepts `Hardware-verified` OR `Software-backed` — devices without a matching root constant still pass; `By.textContains("-verified")` matches only the hardware badge so the `||` covers the software case.)

- [ ] **Step 2: `ReopenE2eTest.kt`:**

```kotlin
package tech.ssemaj.pipe.samplehost

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

private const val TIMEOUT = 10_000L
private const val CRYPTO_TIMEOUT = 20_000L

/** Close + reopen on one PipeView: sequencer reset, surface re-attach, token re-acquisition. */
@RunWith(AndroidJUnit4::class)
class ReopenE2eTest {
    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    @Test fun reopenThenFullRoundTrip() {
        ActivityScenario.launch(MainActivity::class.java)
        assertTrue(device.wait(Until.hasObject(By.text("pane-ready")), TIMEOUT))

        device.findObject(By.text("Reopen")).click()
        assertTrue("pane did not come back after reopen",
            device.wait(Until.hasObject(By.text("pane-ready")), TIMEOUT))

        device.findObject(By.text("Request certification")).click()
        assertTrue(device.wait(Until.hasObject(By.text("Approve")), TIMEOUT))
        device.findObject(By.text("Approve")).click()
        assertTrue("round-trip after reopen failed",
            device.wait(Until.hasObject(By.textContains("-verified")), CRYPTO_TIMEOUT) ||
                device.hasObject(By.text("Software-backed")))
    }
}
```

- [ ] **Step 3: `MultiPaneE2eTest.kt`:**

```kotlin
package tech.ssemaj.pipe.samplehost

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

private const val TIMEOUT = 10_000L
private const val CRYPTO_TIMEOUT = 20_000L

/** Two panes, one provider service: independent consent + round-trips. */
@RunWith(AndroidJUnit4::class)
class MultiPaneE2eTest {
    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    /** Polls until [count] objects match [selector]; UiAutomator has no multi-object SearchCondition. */
    private fun waitForCount(selector: androidx.test.uiautomator.BySelector, count: Int, timeout: Long): Boolean {
        val deadline = android.os.SystemClock.uptimeMillis() + timeout
        while (android.os.SystemClock.uptimeMillis() < deadline) {
            if (device.findObjects(selector).size == count) return true
            Thread.sleep(250)
        }
        return device.findObjects(selector).size == count
    }

    @Test fun twoPanesIndependentRoundTrips() {
        val intent = Intent(ApplicationProvider.getApplicationContext(), MultiPaneActivity::class.java)
        ActivityScenario.launch<MultiPaneActivity>(intent)

        // Both panes render their own consent UI (requests are auto-sent on open).
        assertTrue("two consent panes did not appear",
            waitForCount(By.text("Approve"), 2, TIMEOUT))

        // Approve one pane at a time; each host slot resolves independently.
        device.findObjects(By.text("Approve"))[0].click()
        assertTrue("first pane round-trip failed",
            device.wait(Until.hasObject(By.textEndsWith(": verified")), CRYPTO_TIMEOUT))
        assertEquals("second pane must still await consent", 1, device.findObjects(By.text("Approve")).size)
        device.findObjects(By.text("Approve"))[0].click()
        assertTrue("both panes must verify",
            waitForCount(By.textEndsWith(": verified"), 2, CRYPTO_TIMEOUT))
    }
}
```

- [ ] **Step 4: Verify the untouched suites still compile** — `./gradlew :sample-host:assembleDebugAndroidTest :evil-host:assembleDebugAndroidTest`; BUILD SUCCESSFUL. `SecurityE2eTest`, `SuspendAuthorizerDenialTest`, `EvilHostDeniedTest` are NOT modified — their strings (`denied: `, `EVIL-PANE`, `pane-ready`, `denied: async-policy`) are all produced by the new UI.

- [ ] **Step 5: Device gate** — wake device, install all: `ANDROID_SERIAL=19011FDEE0040L ./gradlew :sample-provider:installDebug :evil-provider:installDebug :evil-host:installDebug :sample-host:installDebug`, then `ANDROID_SERIAL=19011FDEE0040L ./gradlew :sample-host:connectedDebugAndroidTest :evil-host:connectedDebugAndroidTest :sample-provider:connectedDebugAndroidTest`. Expect: sample-host 8 tests (4 PipeE2e + 1 Security + 1 SuspendDenial + 1 Reopen + 1 MultiPane), evil-host 1, sample-provider 1 — all green. Paste results.

- [ ] **Step 6: Root-constant check (only if Task 3 Step 1 left `GOOGLE_ROOTS` empty or the badge shows Software-backed on the Pixel).** Dump the actual root the device returns: temporarily log `Base64.getEncoder().encodeToString(chain.last().publicKey.encoded)` from the verifier (or pull it in a one-off instrumented run), compare with the published Google root, update `GOOGLE_ROOTS`, re-run `CertificationVerifierTest` + the connected suite, and remove any temporary logging.

- [ ] **Step 7: Commit**

```bash
git add -A && git commit -m "test: certification e2e suite with multi-pane and reopen coverage"
```

---

### Task 10: Full verification sweep

**Files:** none new.

- [ ] **Step 1: Whole-repo build + unit tests** — `./gradlew build -x lint`; expect BUILD SUCCESSFUL (includes `:pipe` unit tests, BCV `apiCheck`, contract/host/provider unit tests).

- [ ] **Step 2: Full connected suite once more, from clean installs** — uninstall (`adb uninstall` all four applicationIds), reinstall, run `:sample-host:connectedDebugAndroidTest :evil-host:connectedDebugAndroidTest :sample-provider:connectedDebugAndroidTest`; all green. Paste results.

- [ ] **Step 3: Update the phase-1 plan** — in `docs/superpowers/plans/2026-08-12-pipe-v2-phase1.md`, annotate Task 13 with a pointer: `> Superseded: multi-pane + reopen coverage landed via docs/superpowers/plans/2026-08-12-certification-demo.md`.

- [ ] **Step 4: Commit**

```bash
git add -A && git commit -m "chore: verification sweep for certification demo; supersede phase-1 task 13"
```
