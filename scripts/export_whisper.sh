#!/bin/bash
set -e

# Make sure we are in the project root directory
cd "$(dirname "$0")/.."

echo "⚙️ Creating python3.12 virtual environment in whisper-export-venv..."
python3.12 -m venv whisper-export-venv
source whisper-export-venv/bin/activate

echo "⚙️ Upgrading pip..."
pip install --upgrade pip

echo "⚙️ Installing optimum-executorch..."
pip install optimum-executorch

echo "⚙️ Pinning torch to 2.12.1 to resolve ABI symbol mismatch..."
pip install "torch==2.12.1"


# Check what version of torch got installed
python -c "import torch; print('Installed torch version:', torch.__version__)"

# If there is a need to pin torch, do it here. If the README says torch==2.12.1, let's check if we can install it, but first try the default.
# We will check if we need to resolve any Symbol not found crashes.

echo "⚙️ Exporting Whisper Tiny model..."
optimum-cli export executorch \
  --model openai/whisper-tiny.en \
  --task automatic-speech-recognition \
  --recipe xnnpack \
  --qlinear 8da4w \
  --output_dir whisper_tiny_en_q4

echo "⚙️ Exporting Whisper preprocessor..."
python -m executorch.extension.audio.mel_spectrogram \
  --feature_size 80 --stack_output --max_audio_len 300 \
  --output_file whisper_preprocessor.pte

echo "📁 Copying exported models to assets directory..."
mkdir -p app/src/main/assets
cp whisper_tiny_en_q4/model.pte app/src/main/assets/model.pte
cp whisper_preprocessor.pte app/src/main/assets/whisper_preprocessor.pte

# Check the size of the newly generated model
ls -lh app/src/main/assets/model.pte
ls -lh app/src/main/assets/whisper_preprocessor.pte

echo "✅ Whisper export and setup completed successfully!"
