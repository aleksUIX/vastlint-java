# vastlint-java

**VAST XML validation for JVM ad servers over gRPC.** Drop it into a Prebid Server Java module, a Spring DSP, or an SSAI stitcher. Same IAB-derived catalog as the CLI, [`vastlint-go`](https://github.com/aleksUIX/vastlint-go), and the Erlang binding.

Go embeds the Rust core in-process through CGo. Java does not. A JNI load on a Vert.x event loop is a crash domain you do not want. This client talks to [`vastlint-grpc`](https://github.com/aleksUIX/vastlint/tree/main/crates/vastlint-grpc) over `openadtech.vastlint.v1`. Sub-millisecond validation stays in the Rust process. The JVM gets a blocking stub and a `Verdict`.

**Website and docs:** [VAST tag validator](https://vastlint.org) · **Rule reference:** [VAST error rule reference](https://vastlint.org/docs/rules) · **Methodology:** [How rules are derived](https://vastlint.org/docs/methodology/) · **Web validator:** [validate VAST online](https://vastlint.org/validate)

```kotlin
implementation("io.openadtech:vastlint:0.13.0")
```

GitHub Packages:

```kotlin
repositories {
    maven {
        url = uri("https://maven.pkg.github.com/aleksUIX/vastlint-java")
        credentials {
            username = project.findProperty("gpr.user") as String? ?: System.getenv("GITHUB_ACTOR")
            password = project.findProperty("gpr.key") as String? ?: System.getenv("GITHUB_TOKEN")
        }
    }
}
```

JitPack, no token:

```xml
<repository>
  <id>jitpack.io</id>
  <url>https://jitpack.io</url>
</repository>
<dependency>
  <groupId>com.github.aleksUIX</groupId>
  <artifactId>vastlint-java</artifactId>
  <version>v0.13.0</version>
</dependency>
```

---

## Why trust the rule set?

vastlint-java is a generated client plus a thin facade over the same validation core used by the CLI, web validator, npm package, and other integrations. The rules are traceable to published standards.

- Published IAB VAST XSD schemas are used where IAB ships them: VAST 2.0.1, 3.0, 4.0, 4.1, and 4.2.
- RFC 2119 normative prose in the VAST and SIMID specifications is used where schemas stop.
- VAST 4.3 has no published XSD, so 4.3 rules are derived from the normative IAB spec prose instead.
- Additional standards checks come from W3C XML 1.0, RFC 3986, IANA Media Types, ISO 4217, and Ad-ID.
- If you already run IAB XSD validation in CI (JAXB against `vast_4.2.xsd`), keep it as a baseline. vastlint complements it with prose-derived and operational checks.
- Passing vastlint means the tag is structurally compliant with the published standards. It does not mean every player, SDK, exchange, or business rule will accept the creative unchanged.

---

## Why bother validating VAST?

A broken VAST tag does not fail silently. It burns an impression. The player loads, the auction clears, the publisher gets charged, and the viewer sees nothing. Common causes:

- Missing required fields (`<Impression>`, `<Duration>`, `<MediaFile>`) → player error, no fill
- Malformed XML → parser crash, blank ad slot
- Wrong VAST version declared → player skips the creative
- Wrapper chains that exceed player depth limits → timeout, no ad
- HTTP media URLs in HTTPS contexts → mixed-content block, no playback

Every one of these is detectable in a few hundred microseconds on the server before the impression fires.

---

## Run the server

The client is useless without `vastlint-grpc` in reach.

```sh
docker run --rm -p 50051:50051 aleksuix/vastlint-grpc:0.13.0
```

Or from the vastlint repo:

```sh
cargo run --release -p vastlint-grpc
```

Health and a one-shot check without this library:

```sh
grpcurl -plaintext localhost:50051 grpc.health.v1.Health/Check
grpcurl -plaintext -d '{"document":"<VAST version=\"4.1\"></VAST>"}' \
  localhost:50051 openadtech.vastlint.v1.VastlintService/Validate
```

---

## Use cases

### 1. Pre-bid creative rejection

Validate the winning bid's VAST before accepting it. If it is broken, fall to the next eligible bid.

```java
try (VastlintClient client = VastlintClient.connect("vastlint:50051")) {
    for (Bid bid : rankedBids) {
        Verdict verdict = client.validate(bid.vastXml());
        if (verdict.getValid()) {
            return bid;
        }
        log.warn("bid {} rejected: {} errors: {}",
                bid.id(), verdict.getSummary().getErrors(),
                verdict.getIssuesCount() == 0 ? "" : verdict.getIssues(0).getMessage());
    }
    throw new NoValidBidException();
}
```

### 2. Async quality monitoring

Fire validation after the bid is sent. Collect quality signals per demand partner without touching the critical path.

```java
executor.execute(() -> {
    Verdict verdict = client.validate(bid.vastXml());
    metrics.recordCreativeQuality(bid.demandPartner(), verdict);
});
```

### 3. Wrapper depth enforcement

```java
Verdict verdict = client.validate(xml, VastlintClient.Options.defaults()
        .wrapperDepth(depth)
        .maxWrapperDepth(4)
        .override("VAST-4.1-mezzanine-recommended", "off"));
```

---

## API

```java
import io.openadtech.vastlint.VastlintClient;
import io.openadtech.vastlint.v1.Verdict;

try (VastlintClient client = VastlintClient.connect("localhost:50051")) {
    Verdict verdict = client.validate(xml);
    if (!verdict.getValid()) {
        verdict.getIssuesList().forEach(issue ->
                System.err.printf("[%s] %s (%s)%n",
                        issue.getSeverity(), issue.getMessage(), issue.getRuleId()));
    }

    client.fix(xml);
    client.listRules();
}
```

`connect` is plaintext. `connectTls` is for a load balancer that terminates TLS. `fromChannel` is for tests and for callers that already own a `ManagedChannel`.

Default deadline is 5 seconds. Pass a `Duration` to `connect` to change it.

Empty documents throw `IllegalArgumentException` before the RPC, matching `vastlint-go`. Server failures throw `VastlintException`.

Wire types (`Verdict`, `Issue`, `ValidationContext`, …) live in `io.openadtech.vastlint.v1` and are generated from [`vastlint.proto`](src/main/proto/openadtech/vastlint/v1/vastlint.proto). The contract is owned by [aleksUIX/vastlint](https://github.com/aleksUIX/vastlint). This repo vendors it and tags at the same version.

Requires Java 11 or newer.

---

## License

Apache License 2.0. See [LICENSE](LICENSE).
