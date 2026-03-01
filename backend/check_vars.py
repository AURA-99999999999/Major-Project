#!/usr/bin/env python3
"""Check for undefined variables in collaborative_service.py"""

with open('services/collaborative_service.py', 'r', encoding='utf-8') as f:
    lines = f.readlines()

# Look for uses of undefined variables
problematic = []

for i, line in enumerate(lines, 1):
    if 'artist_track_count' in line and 'artist_track_count =' not in line:
        problematic.append(f"Line {i}: artist_track_count (undefined variable)")
    if 'collection_stats' in line and 'collection_stats =' not in line:
        problematic.append(f"Line {i}: collection_stats (undefined variable)")

if problematic:
    print("Found undefined variables:")
    for prob in problematic:
        print(f"  ⚠ {prob}")
else:
    print("✓ No obviously undefined variables found")

# Try to compile
try:
    import py_compile
    py_compile.compile('services/collaborative_service.py', doraise=True)
    print("✓ File compiles successfully")
except Exception as e:
    print(f"✗ Compilation error: {e}")
