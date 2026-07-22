# Whisper model assets

The app loads three files from this `assets/` folder at runtime. They are **not**
checked in — you generate/download them once and drop them here:

| File | What it is | How to get it |
|------|------------|---------------|
| `model.pte` | Whisper encoder + decoder, XNNPACK (mobile CPU) | export command below |
| `whisper_preprocessor.pte` | log-mel spectrogram front-end | export command below |
| `tokenizer.json` | vocab + byte-level BPE rules | download from HuggingFace |

## 1. Export the model + preprocessor (dev machine, Python)

> **Prerequisites — avoid two install failures we already hit:**
> - **Use Python 3.10–3.12** (not 3.13/3.14). ExecuTorch has no wheels for 3.13+, so
>   `pip install optimum-executorch` fails with *"No matching distribution found for
>   executorch"*. macOS: `python3.12 -m venv ~/whisper-export-venv && source ~/whisper-export-venv/bin/activate`.
> - **Pin torch to 2.12.x for executorch 1.3.1.** Its metadata says `torch>=2.12.0a0`,
>   which lets pip grab torch 2.13.0 — an ABI mismatch that crashes with
>   *"Symbol not found: ...materialize_cow_storage ... Expected in libc10.dylib"*.
>   After installing, run `pip install "torch==2.12.1"` to force the matching build.

```bash
pip install optimum-executorch        # plus torch + transformers per its README
pip install "torch==2.12.1"           # match executorch 1.3.1 (our Android runtime)

# Encoder+decoder graph, XNNPACK backend, int8-dynamic-activation / int4-weight quant
optimum-cli export executorch \
  --model openai/whisper-tiny.en \
  --task automatic-speech-recognition \
  --recipe xnnpack \
  --qlinear 8da4w \
  --output_dir whisper_tiny_en_q4       # produces model.pte (~189 MB)

# Log-mel spectrogram preprocessor (80 mel bins for tiny)
python -m executorch.extension.audio.mel_spectrogram \
  --feature_size 80 --stack_output --max_audio_len 300 \
  --output_file whisper_preprocessor.pte
```

Copy `model.pte` and `whisper_preprocessor.pte` into this folder.

## 2. Tokenizer

Download `tokenizer.json` from https://huggingface.co/openai/whisper-tiny.en into this
folder. (The Kotlin `WhisperTokenizer` reads it directly — no other tokenizer files are
needed at runtime.)

## Verified model signatures

These were confirmed by introspecting the exported `model.pte` — the Kotlin in
`asr/WhisperModel.kt` is written against exactly these:

- `encoder(mel[1,80,3000] f32) -> hidden[1,1500,384] f32`
- `text_decoder(input_ids[1,1] i64, hidden[1,1500,384] f32, cache_position[1] i64) -> logits[1,1,51864] f32`
  (stateful KV-cache in the module's mutable buffers)
- config: `decoder_start_token_id=50257`, `eos=50256`, `vocab_size=51864`, `max_seq_len=1024`

## Notes
- `.pte` files are kept uncompressed in the APK (`noCompress += "pte"` in build.gradle)
  so ExecuTorch can mmap them; the app copies all three to `filesDir` on first launch.
- Only `arm64-v8a` is built — run on a physical arm64 device, not the x86 emulator.
