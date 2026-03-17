#!/usr/bin/env python3
"""
Lightweight API smoke tests for core endpoints.
"""
from __future__ import annotations


import subprocess
import sys
import time
from pathlib import Path
import os
import requests


def main() -> int:
    backend_root = Path(__file__).resolve().parents[1]

    print("=" * 60)
    print("Testing Flask API Endpoints")
    print("=" * 60)

    proc = subprocess.Popen(
        [sys.executable, str(backend_root / "app.py")],
        cwd=str(backend_root),
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
    )

    time.sleep(3)

    BASE_URL = os.getenv("BASE_URL", "http://localhost:5000")
    try:
        # Test /api/trending endpoint
        print("\n\n>>> Testing /api/trending endpoint")
        print("-" * 60)
        response = requests.get(f"{BASE_URL}/api/trending", timeout=15)

        if response.status_code == 200:
            data = response.json()
            print(f"OK: {data.get('count')} trending songs")
        else:
            print(f"FAIL: {response.status_code} {response.text}")
            return 1

        # Test /api/home endpoint
        print("\n\n>>> Testing /api/home endpoint")
        print("-" * 60)
        response = requests.get(f"{BASE_URL}/api/home?limit=5", timeout=15)

        if response.status_code == 200:
            data = response.json()
            print(f"OK: {data.get('count')} items in home")
        else:
            print(f"FAIL: {response.status_code} {response.text}")
            return 1

        return 0
    finally:
        print("\n\nStopping Flask app...")
        proc.terminate()
        proc.wait(timeout=10)


if __name__ == "__main__":
    raise SystemExit(main())
