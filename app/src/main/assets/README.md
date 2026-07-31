# assets/

The three Whisper files ship here, in the APK:

| File | Size | What it is |
|------|------|------------|
| `model.pte` | 70 MiB | Whisper tiny.en encoder + decoder, XNNPACK |
| `whisper_preprocessor.pte` | 77 KiB | log-mel spectrogram front-end |
| `tokenizer.json` | 2.3 MiB | vocab + byte-level BPE rules |

They are bundled rather than downloaded because no public repo has a `whisper-tiny.en`
ExecuTorch export with the method signatures `WhisperModel.kt` expects — the ones on the hub
are either the multilingual `whisper-tiny` or a different runtime's export. These are local
`optimum-cli` exports, so the APK is the only place they can come from without hosting.

All three are listed in `noCompress` in `app/build.gradle.kts`, `json` included. That matters:
a compressed asset has no readable length, so `ModelStore` cannot tell a complete extracted
copy from a truncated one, and a bundled `tokenizer.json` would resolve as "not downloaded
yet".

The two Gemma models are **not** here — together they are around 840 MB. They download at
runtime instead. See [MODELS.md](../../../../MODELS.md) for where they come from and how to
point a build at them.

## Replacing a Whisper file

`ModelCatalog` records each file's size and SHA-256. Those are only enforced on a download, so
a bundled file is trusted and a swap works immediately — but update the catalog anyway, or the
metadata silently describes a file you no longer ship.

```bash
shasum -a 256 app/src/main/assets/model.pte
```
