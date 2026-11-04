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
- Reranks up to ten candidate embeddings by cosine similarity and protects
  Mozc ordering with a minimum improvement margin, exception fallback, and
  an in-memory cache. This is a **simplified prototype**, not the complete
  Fumiori multi-segment decoder or ranking formula.
- Pure Kotlin scorer unit tests use deterministic vectors and do not claim
  to validate actual model quality.

## Local build

```sh
cd android
./gradlew :onnx-probe:testDebugUnitTest :onnx-probe:assembleDebug
```

The Windows Fumiori installer is **not** an Android model and is never
installed on a device. For real inference, obtain the current Fumiori v2.1
Dual-Encoder **INT8** ONNX file and its matching tokenizer.json, check the
redistribution rights and SHA-256, then put the model in an app-private path.
ONNX input signature: `input_ids`, `attention_mask` (int64, batch=1).
Output: `embeddings` (float32, shape [1, 384]).

### Unimplemented / do not claim yet

1. Exact SentencePiece tokenizer (Ruri-v3/ModernBERT) on Android. A tokenizer
   mismatch makes the output meaningless even if ONNX runs successfully.
2. Model acquisition and availability of the **current Dual-Encoder** weights.
   The older `model-manifest.json` refers to an older Cross-Encoder and must
   not be used with this runtime.
3. Measure cold start, p50/p95, memory, battery and model accuracy on
   Pixel ARM64 with real tokenized fixture pairs. The ONNX part by itself does
   not demonstrate interactive-latency compliance.
4. Integrate the ranker between `MozcConverter.convert` and the shared C#
   core's `CoreMozcCallbacks`, with explicit opt-in and asynchronous
   deadlines/cancellation. Do not block the IME main thread.
5. Fumiori's word-boundary/grammatical scores, multi-clause joint path and
   persistent 384-dim candidate cache, plus provenance/license notices.

Links:
- https://github.com/YAMA-TANA/fumiori-ai-ime
- https://github.com/YAMA-TANA/fumiori-ai-ime/blob/main/ranker/onnx_dual_encoder_base.py
- https://github.com/YAMA-TANA/fumiori-ai-ime/blob/main/MODEL_CARD.md

## Tested with an actual Android ONNX execution path

`OnnxCpuSmokeTest` runs a **275-byte synthetic Cast/Multiply ONNX graph** on
an Android 15 emulator, checks the exact float32 tensor output and logs p50/p95
for that graph. This establishes that ORT's Android CPU JNI execution path
works, **not** that Fumiori's 70M model loads or meets an IME deadline.
