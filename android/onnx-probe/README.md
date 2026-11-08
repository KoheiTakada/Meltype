# Local ONNX reranker — Android proof of concept

Experimental, isolated module derived from Meltype PR #1149. **It is not linked
to the production keyboard**; the PR remains untouched and the normal Mozc
conversion is unchanged. The experiment sends no text to a network service.

## Verified design

- Runs the ONNX graph on Android with **CPUExecutionProvider**, without CUDA
  or DirectML. Uses onnxruntime-android 1.31.1.
- Model file is loaded from an app-private absolute path (not shipped).
- Accepts token ID + attention mask pairs, runs the Fumiori-style dual encoder
  and returns its 384-dimensional float embedding.
- Uses the model's own tokenizer on Android. Five Japanese, mixed-language and
  long inputs matched the Python tokenizer's token IDs in the emulator test.
- Reranks up to ten candidate embeddings by cosine similarity and protects
  Mozc ordering with a minimum improvement margin, exception fallback, and
  an in-memory cache. This is a **simplified prototype**, not the complete
  Fumiori multi-segment decoder or ranking formula.
- Pure Kotlin scorer unit tests use deterministic vectors and do not claim
  to validate actual model quality.

## Local build

```sh
cd android/onnx-probe/tokenizer
cargo ndk -t arm64-v8a -t x86_64 -P 26 -o ../src/main/jniLibs build --release
cd ../..
./gradlew :onnx-probe:testDebugUnitTest :onnx-probe:assembleDebug
```

The Windows Fumiori installer is **not** an Android model and is never
installed on a device. For real inference, obtain the current Fumiori v2.1
Dual-Encoder **INT8** ONNX file and its matching tokenizer.json, check the
redistribution rights and SHA-256, then put the model in an app-private path.
ONNX input signature: `input_ids`, `attention_mask` (int64, batch=1).
Output: `embeddings` (float32, shape [1, 384]).

### Unimplemented / do not claim yet

1. Decide how the **current Dual-Encoder** model will be installed and
   attributed before integrating it into the IME. The older
   `model-manifest.json` refers to an older Cross-Encoder and must not be used.
2. Measure cold start, p50/p95, memory, battery and model accuracy on
   Pixel ARM64 with real tokenized fixture pairs. The ONNX part by itself does
   not demonstrate interactive-latency compliance.
3. Integrate the ranker between `MozcConverter.convert` and the shared C#
   core's `CoreMozcCallbacks`, with explicit opt-in and asynchronous
   deadlines/cancellation. Do not block the IME main thread.
4. Fumiori's word-boundary/grammatical scores, multi-clause joint path and
   persistent 384-dim candidate cache, plus provenance/license notices.

Links:
- https://github.com/YAMA-TANA/fumiori-ai-ime
- https://github.com/YAMA-TANA/fumiori-ai-ime/blob/main/ranker/onnx_dual_encoder_base.py
- https://github.com/YAMA-TANA/fumiori-ai-ime/blob/main/MODEL_CARD.md

## Tested with an actual Android ONNX execution path

`OnnxCpuSmokeTest` runs a **275-byte synthetic Cast/Multiply ONNX graph** on
an Android 11 emulator, checks the exact float32 tensor output and logs p50/p95
for that graph. This establishes that ORT's Android CPU JNI execution path
works, **not** that Fumiori's 70M model loads or meets an IME deadline.

## Optional real model benchmark (not bundled)

`.github/workflows/android-onnx-real-model.yml` downloads Fumiori's official
v2.1.1-beta Windows MSI, verifies the publisher's SHA-256, extracts the current
Dual-Encoder INT8 ONNX and matching tokenizer.json, and prepares exact token
IDs with Hugging Face `tokenizers` on the CI host. It then runs the model on an
Android 11 x86_64 emulator via the Android ONNX Runtime CPU provider and logs
model load time and p50/p95 for seven context embedding runs. If MSI extraction
or required files fail, the workflow fails rather than silently substituting
the old Cross-Encoder or reporting bogus inference numbers. Models are never
committed or uploaded as CI artifacts. Emulator results are NOT representative
of Pixel ARM64 latency. Real-time IME integration remains a separate step.

On 2026-11-09, [run 42498604547](https://github.com/KoheiTakada/Meltype/actions/runs/42498604547)
tested commit `4cbe92c`: the real model, native tokenizer parity and synthetic
ONNX test passed on the Android 11 x86_64 emulator (3/3); the ranking unit tests
passed (4/4). The model was 70,538,845 bytes; initialization was 288 ms, and
seven warmed context encodes took p50 6.84 ms / p95 7.05 ms. ARM64 native
libraries also compiled. These emulator numbers are not Pixel measurements,
end-to-end keyboard latency, or an accuracy benchmark.
