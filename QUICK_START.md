# Quick Start Guide

## 🚀 Get Started in 5 Minutes

### Prerequisites Check
- ✅ Python 3.8+ installed
- ✅ Node.js 16+ installed
- ✅ Terminal/Command Prompt open

### Step 1: Backend (2 minutes)

```bash
# Navigate to project
cd "F:\Major test project\DeciBel"

# Activate virtual environment
venv\Scripts\activate

# Install dependencies (if not already installed)
pip install -r requirements.txt

# Start backend server
python app.py
```

✅ Backend running on http://localhost:5000

### Step 2: Frontend (2 minutes)

Open a **NEW** terminal window:

```bash
# Navigate to frontend
cd "F:\Major test project\DeciBel\frontend"

# Install dependencies (if not already installed)
npm install

# Start frontend server
npm run dev
```

✅ Frontend running on http://localhost:3000

### Step 3: Use the App (1 minute)

1. Open browser: http://localhost:3000
2. Search for a song (e.g., "Imagine Dragons Believer")
3. Click on a song to play
4. Explore features!

## 🎯 Quick Feature Tour

### Home Page
- Trending songs
- Your playlists
- Recently played

### Search
- Search millions of songs
- Click to play instantly

### Playlists
- Create custom playlists
- Add songs from search
- Manage your library

### Player
- Mini player (bottom bar)
- Full player (click mini player)
- Volume, shuffle, repeat controls

### Profile (Login Required)
- Liked songs
- Recently played history

## 📝 Common Commands

### Backend
```bash
# Start server
python app.py

# Install new package
pip install package-name
pip freeze > requirements.txt
```

### Frontend
```bash
# Start dev server
npm run dev

# Build for production
npm run build

# Preview production build
npm run preview

# Install new package
npm install package-name
```

## 🐛 Quick Troubleshooting

### "Port already in use"
- Backend: Change port in `app.py` (line 62)
- Frontend: Vite auto-finds next port

### "Module not found"
- Backend: `pip install -r requirements.txt`
- Frontend: `npm install`

### "Cannot connect to API"
- Check backend is running on port 5000
- Check browser console for errors
- Verify proxy in `vite.config.js`

### Songs won't play
- Check browser console
- Try different song
- Check internet connection

## 📚 Next Steps

1. Read [README.md](README.md) for detailed documentation
2. Check [INTEGRATION.md](INTEGRATION.md) for advanced setup
3. Explore the codebase structure
4. Customize the UI and features

## 🎨 Customization Tips

### Change Colors
Edit `frontend/tailwind.config.js` → `colors` section

### Add Features
1. Backend: Add service method in `services/`
2. Frontend: Add component in `components/`
3. Connect: Use services in `services/`

### Modify UI
- Components: `frontend/src/components/`
- Pages: `frontend/src/pages/`
- Styles: `frontend/src/index.css`

## 💡 Pro Tips

1. **Keep both servers running** during development
2. **Check browser console** for errors
3. **Use React DevTools** for debugging
4. **Hot reload** works automatically
5. **Backend logs** show in terminal

## 🔗 Important URLs

- Frontend: http://localhost:3000
- Backend API: http://localhost:5000
- API Health: http://localhost:5000/api/health

## ✨ Enjoy Building!

You now have a production-grade music streaming app ready to customize!

