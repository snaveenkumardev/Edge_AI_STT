# App Assets Directory

Place your MediaPipe / FunctionGemma binary model file here:
- Name: `function_gemma.bin` (or `gemma-2b-it-gpu-int4.bin` renamed to `function_gemma.bin`)

When the application launches, `FunctionGemmaMediaPipeEngine` will automatically copy `function_gemma.bin` to internal app storage (`context.filesDir`) and initialize `llmInference`.
