import sys
from pathlib import Path


BACKEND_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(BACKEND_ROOT))

from services.jiosaavn_service import JioSaavnService
s = JioSaavnService()
r = s.search_all_categories("arijit", 5)
print("Songs:", len(r["songs"]))
print("Albums:", len(r["albums"]))
print("Playlists:", len(r["playlists"]))
if r["albums"]:
    print("First album:", r["albums"][0]["title"])
