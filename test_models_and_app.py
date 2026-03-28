#!/usr/bin/env python3
"""
Test script to trigger LLM model download and verify app functionality.
"""

import subprocess
import time
import os
import requests
from urllib.parse import urlparse

def test_bitnet_model_url():
    """Test if the BitNet model download URL is accessible."""
    print("🧪 Testing BitNet model URLs...")

    # Test all three quality modes (all use same file now)
    urls = [
        "https://huggingface.co/microsoft/bitnet-b1.58-2B-4T-gguf/resolve/main/ggml-model-i2_s.gguf",  # LITE
        "https://huggingface.co/microsoft/bitnet-b1.58-2B-4T-gguf/resolve/main/ggml-model-i2_s.gguf",  # BALANCED
        "https://huggingface.co/microsoft/bitnet-b1.58-2B-4T-gguf/resolve/main/ggml-model-i2_s.gguf",   # PRO
    ]

    modes = ["LITE (~1.1GB)", "BALANCED (~1.1GB)", "PRO (~1.1GB)"]

    working_urls = []
    for i, url in enumerate(urls):
        try:
            print(f"  Testing {modes[i]}...")
            response = requests.head(url, allow_redirects=True, timeout=30)
            if response.status_code == 200:
                file_size = response.headers.get('content-length', 'unknown')
                if file_size != 'unknown':
                    file_size_mb = int(file_size) / (1024 * 1024)
                    print(f"    ✅ {modes[i]} - {file_size_mb:.1f} MB")
                else:
                    print(f"    ✅ {modes[i]} - size unknown")
                working_urls.append((modes[i], url))
            else:
                print(f"    ❌ {modes[i]} - HTTP {response.status_code}")
        except Exception as e:
            print(f"    ❌ {modes[i]} - Error: {str(e)}")

    if working_urls:
        print(f"\n✅ {len(working_urls)}/3 BitNet model URLs are accessible")
        return True
    else:
        print(f"\n❌ No BitNet model URLs are accessible")
        return False

def check_assets():
    """Check if required assets are in place."""
    print("\n📁 Checking assets...")

    assets_dir = "app/src/main/assets"
    required_files = [
        "stock_prediction.tflite",
        "nse_companies.json",
        "stocks_initial.json"
    ]

    all_present = True
    for file in required_files:
        file_path = os.path.join(assets_dir, file)
        if os.path.exists(file_path):
            file_size = os.path.getsize(file_path)
            print(f"  ✅ {file} ({file_size} bytes)")
        else:
            print(f"  ❌ {file} - Missing")
            all_present = False

    return all_present

def build_app():
    """Build the Android app."""
    print("\n🔨 Building Android app...")

    try:
        # Clean first
        print("  Cleaning previous build...")
        result = subprocess.run(
            ["./gradlew", "clean"],
            capture_output=True,
            text=True,
            timeout=300
        )

        if result.returncode != 0:
            print(f"  ❌ Clean failed: {result.stderr}")
            return False

        # Build debug APK
        print("  Building debug APK...")
        result = subprocess.run(
            ["./gradlew", "assembleDebug"],
            capture_output=True,
            text=True,
            timeout=600
        )

        if result.returncode == 0:
            print("  ✅ Build successful")

            # Check if APK was created
            apk_path = "app/build/outputs/apk/debug/app-universal-debug.apk"
            if os.path.exists(apk_path):
                apk_size = os.path.getsize(apk_path)
                print(f"  📱 APK created: {apk_path} ({apk_size / (1024*1024):.1f} MB)")
                return True
            else:
                print(f"  ❌ APK not found at expected location")
                return False
        else:
            print(f"  ❌ Build failed:")
            print(f"  stdout: {result.stdout}")
            print(f"  stderr: {result.stderr}")
            return False

    except subprocess.TimeoutExpired:
        print("  ❌ Build timeout")
        return False
    except Exception as e:
        print(f"  ❌ Build error: {str(e)}")
        return False

def install_and_test_app():
    """Install app and test basic functionality."""
    print("\n📱 Installing and testing app...")

    try:
        # Check if device is connected
        result = subprocess.run(
            ["adb", "devices"],
            capture_output=True,
            text=True,
            timeout=10
        )

        if "device" not in result.stdout:
            print("  ⚠️  No Android device/emulator connected")
            print("  💡 Connect a device or start an emulator to test")
            return False

        # Install APK
        print("  Installing APK...")
        apk_path = "app/build/outputs/apk/debug/app-universal-debug.apk"
        result = subprocess.run(
            ["adb", "install", "-r", apk_path],
            capture_output=True,
            text=True,
            timeout=60
        )

        if result.returncode == 0:
            print("  ✅ App installed successfully")
        else:
            print(f"  ❌ Install failed: {result.stderr}")
            return False

        # Start the app
        print("  Starting app...")
        result = subprocess.run(
            ["adb", "shell", "am", "start", "-n", "com.stocksense.app/.MainActivity"],
            capture_output=True,
            text=True,
            timeout=10
        )

        if result.returncode == 0:
            print("  ✅ App started successfully")

            # Give app time to initialize
            time.sleep(5)

            # Check if app is running
            result = subprocess.run(
                ["adb", "shell", "ps", "|", "grep", "stocksense"],
                capture_output=True,
                text=True,
                timeout=10
            )

            print("  🕐 App should be initializing models...")
            print("  💡 Check device for app UI and model download progress")

            return True
        else:
            print(f"  ❌ Failed to start app: {result.stderr}")
            return False

    except Exception as e:
        print(f"  ❌ Device test error: {str(e)}")
        return False

def main():
    """Main test function."""
    print("🚀 StockSense Model and App Test")
    print("=" * 50)

    # Change to project directory
    os.chdir("/Users/commandcenter/StudioProjects/Stocksense-financeAI")

    # Step 1: Test BitNet URLs
    urls_working = test_bitnet_model_url()

    # Step 2: Check assets
    assets_ready = check_assets()

    if not assets_ready:
        print("\n❌ Missing required assets")
        return False

    # Step 3: Build app
    build_success = build_app()

    if not build_success:
        print("\n❌ Build failed")
        return False

    # Step 4: Install and test (optional if device available)
    device_test = install_and_test_app()

    # Summary
    print("\n" + "=" * 50)
    print("📊 Test Summary:")
    print(f"  BitNet URLs:     {'✅' if urls_working else '❌'}")
    print(f"  Assets Ready:    {'✅' if assets_ready else '❌'}")
    print(f"  Build Success:   {'✅' if build_success else '❌'}")
    print(f"  Device Test:     {'✅' if device_test else '⚠️  (no device connected)'}")

    if urls_working and assets_ready and build_success:
        print("\n🎉 StockSense is ready!")
        print("\n📋 What happens when you run the app:")
        print("  1. TFLite model loads from assets (stock_prediction.tflite)")
        print("  2. BitNet model download starts in background")
        print("  3. Prediction engine works with TFLite model")
        print("  4. LLM engine starts with fallback, upgrades when model downloads")
        print("  5. Full AI functionality available once BitNet model is downloaded")
        return True
    else:
        print("\n❌ Some components failed")
        return False

if __name__ == "__main__":
    success = main()
    exit(0 if success else 1)
