#!/usr/bin/env python3
"""
MediaPipe LLM Model Quantization & Setup Script for Edge AI STT

This script prepares a 4-bit INT4 quantized Gemma / FunctionGemma model binary
for direct deployment into the Android app's assets directory (app/src/main/assets/function_gemma.bin).

Usage:
  # Programmatically download and quantize google/functiongemma-270m-it:
  python3 scripts/quantize_gemma.py --hf_model google/functiongemma-270m-it
"""

import os
import sys
import shutil
import argparse
import urllib.request

ASSETS_DIR = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "app", "src", "main", "assets")
OUTPUT_BIN_PATH = os.path.join(ASSETS_DIR, "function_gemma.task")

def ensure_assets_dir():
    if not os.path.exists(ASSETS_DIR):
        os.makedirs(ASSETS_DIR, exist_ok=True)
        print(f"📁 Created assets directory at: {ASSETS_DIR}")

def convert_local_checkpoint(input_dir):
    ensure_assets_dir()
    print(f"⚙️ Quantizing model from: {input_dir}")
    
    try:
        import mediapipe as mp
        from mediapipe.tasks.python.genai import converter

        # Configures the MediaPipe GenAI converter with all required parameters
        config = converter.ConversionConfig(
            input_ckpt=input_dir,
            ckpt_format='safetensors',
            model_type='GEMMA_2B', # Matches standard Gemma-based tensor mapping
            backend='cpu',         # Standard CPU execution graph
            output_dir=ASSETS_DIR,
            combine_file_only=True,
            vocab_model_file=os.path.join(input_dir, "tokenizer.model")
        )
        print("⏳ Running MediaPipe GenAI Converter (INT4 Quantization)...")
        converter.convert_checkpoint(config)
        
        # Locate the output binary and rename it to function_gemma.bin
        converted_file = os.path.join(ASSETS_DIR, "model.bin")
        if os.path.exists(converted_file):
            if os.path.exists(OUTPUT_BIN_PATH):
                os.remove(OUTPUT_BIN_PATH)
            os.rename(converted_file, OUTPUT_BIN_PATH)
            print(f"✅ Quantization complete! Model saved to: {OUTPUT_BIN_PATH}")
        else:
            # Check if any .bin got generated
            bin_files = [f for f in os.listdir(ASSETS_DIR) if f.endswith(".bin") and f != "function_gemma.bin"]
            if bin_files:
                src_path = os.path.join(ASSETS_DIR, bin_files[0])
                if os.path.exists(OUTPUT_BIN_PATH):
                    os.remove(OUTPUT_BIN_PATH)
                os.rename(src_path, OUTPUT_BIN_PATH)
                print(f"✅ Quantization complete! Renamed {bin_files[0]} to: {OUTPUT_BIN_PATH}")
            else:
                print("⚠️ Converter completed, but could not find output model.bin or model asset.")

    except ImportError:
        print("⚠️ `mediapipe` python package not found.")
        print("Install required quantization dependencies:")
        print("  pip install mediapipe huggingface_hub torch")
    except Exception as e:
        print(f"❌ Quantization error: {e}")

def download_and_quantize_hf_model(model_id):
    ensure_assets_dir()
    print(f"📥 Fetching model weights from Hugging Face: {model_id}")
    
    try:
        from huggingface_hub import hf_hub_download, snapshot_download
        
        # If it is google/functiongemma-270m-it, download the pre-compiled MediaPipe task bundle directly
        if "functiongemma-270m-it" in model_id:
            print("📦 Found pre-bundled MediaPipe task target: functiongemma-270M-it.task")
            # sasha-denisov/function-gemma-270M-it provides the compiled MediaPipe bundle (contains both weights and tokenizer)
            downloaded_file = hf_hub_download(
                repo_id="sasha-denisov/function-gemma-270M-it",
                filename="functiongemma-270M-it.task"
            )
            if os.path.exists(OUTPUT_BIN_PATH):
                os.remove(OUTPUT_BIN_PATH)
            shutil.copy(downloaded_file, OUTPUT_BIN_PATH)
            print(f"✅ Successfully downloaded and configured: {OUTPUT_BIN_PATH}")
            return

        # Set up a temporary folder for raw checkpoint weights
        temp_dir = os.path.join(ASSETS_DIR, "temp_hf_checkpoint")
        if os.path.exists(temp_dir):
            shutil.rmtree(temp_dir)
        os.makedirs(temp_dir, exist_ok=True)

        print(f"⏳ Downloading files to: {temp_dir}")
        # snapshot_download will automatically handle auth tokens if logged in via huggingface-cli
        snapshot_download(
            repo_id=model_id,
            local_dir=temp_dir,
            ignore_patterns=["*.msgpack", "*.h5", "*.ot", "*.bin.jar"]
        )
        
        # Quantize the downloaded raw checkpoint
        convert_local_checkpoint(temp_dir)
        
        # Clean up the raw temp files to save space
        shutil.rmtree(temp_dir)
        print("🧹 Temporary checkpoint files cleared.")

    except ImportError:
        print("⚠️ `huggingface_hub` python package not found.")
        print("Install dependencies: pip install huggingface_hub mediapipe torch")
    except Exception as e:
        print(f"❌ Download/Quantization failed: {e}")

def main():
    parser = argparse.ArgumentParser(description="MediaPipe LLM Model Setup & Quantization Tool")
    parser.add_argument("--hf_model", type=str, help="Hugging Face model repository ID to download and quantize (e.g. google/functiongemma-270m-it)")
    parser.add_argument("--convert", action="store_true", help="Convert local PyTorch / HuggingFace Gemma checkpoint directory")
    parser.add_argument("--input_dir", type=str, help="Path to local HuggingFace / PyTorch Gemma checkpoint directory")

    args = parser.parse_args()

    if args.hf_model:
        download_and_quantize_hf_model(args.hf_model)
    elif args.convert and args.input_dir:
        convert_local_checkpoint(args.input_dir)
    else:
        parser.print_help()
        print("\nQuick Start Command:")
        print("  python3 scripts/quantize_gemma.py --hf_model google/functiongemma-270m-it")

if __name__ == "__main__":
    main()
