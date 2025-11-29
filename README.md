# Aura - Production-Grade Music Streaming Platform

A modern, full-stack music streaming application built with Flask (backend) and React (frontend), featuring a premium UI/UX similar to Spotify and Apple Music.

## 🎵 Features

### Core Features
- **Music Search & Discovery**: Search millions of songs using YouTube Music API
- **Audio Streaming**: High-quality audio streaming using yt-dlp
- **Playlists**: Create, manage, and share playlists
- **User Authentication**: Secure user registration and login
- **User Profiles**: Track liked songs and recently played music
- **Queue System**: Advanced queue management with shuffle and repeat modes
- **Responsive Design**: Works seamlessly on desktop, tablet, and mobile

### UI/UX Features
- **Modern Design**: Glass-morphism UI with gradient accents and neon glow effects
- **Dark/Light Mode**: Theme switching support
- **Smooth Animations**: Framer Motion powered transitions
- **Mini Player**: Sticky bottom player that stays accessible while browsing
- **Full Player**: Immersive full-screen player experience
- **Loading States**: Skeleton loaders and smooth loading indicators
- **Error Handling**: Comprehensive error boundaries and user-friendly error messages

## 🏗️ Architecture

### Backend (Flask)
```
DeciBel/
├── app.py                 # Main Flask application
├── config.py              # Configuration settings
├── services/
│   ├── music_service.py   # Music search and streaming logic
│   ├── playlist_service.py # Playlist management
│   └── user_service.py    # User authentication and profile
└── requirements.txt       # Python dependencies
```

### Frontend (React)
```
frontend/
├── src/
│   ├── components/
│   │   ├── UI/           # Reusable UI components
│   │   ├── Music/        # Music-related components
│   │   ├── Player/       # Audio player components
│   │   └── Layout/       # Layout components
│   ├── pages/            # Route pages
│   ├── services/         # API service layer
│   ├── context/          # React Context providers
│   └── App.jsx           # Main app component
├── package.json
└── vite.config.js
```

## 🚀 Getting Started

### Prerequisites

- Python 3.8+ 
- Node.js 16+ and npm/yarn
- Virtual environment (recommended)

### Backend Setup

1. **Navigate to the backend directory**:
   ```bash
   cd DeciBel
   ```

2. **Create and activate a virtual environment**:
   ```bash
   python -m venv venv
   
   # On Windows:
   venv\Scripts\activate
   
   # On macOS/Linux:
   source venv/bin/activate
   ```

3. **Install Python dependencies**:
   ```bash
   pip install -r requirements.txt
   ```

4. **Run the Flask server**:
   ```bash
   python app.py
   ```

   The backend will start on `http://localhost:5000`

### Frontend Setup

1. **Navigate to the frontend directory**:
   ```bash
   cd frontend
   ```

2. **Install Node dependencies**:
   ```bash
   npm install
   # or
   yarn install
   ```

3. **Start the development server**:
   ```bash
   npm run dev
   # or
   yarn dev
   ```

   The frontend will start on `http://localhost:3000`

### Data Storage

The app uses JSON files for data storage (in-memory for development):
- Playlists: `data/playlists.json`
- Users: `data/users.json`

**Note**: For production, you should replace these with a proper database (PostgreSQL, MongoDB, etc.)

## 📡 API Endpoints

### Music Endpoints
- `GET /api/search?query=<query>&limit=<limit>` - Search for songs
- `GET /api/song/<video_id>` - Get song details and streaming URL
- `GET /api/trending?limit=<limit>` - Get trending songs
- `GET /api/artist/<artist_id>` - Get artist information

### Playlist Endpoints
- `GET /api/playlists?userId=<user_id>` - Get all playlists
- `POST /api/playlists` - Create a new playlist
- `GET /api/playlists/<playlist_id>` - Get playlist details
- `PUT /api/playlists/<playlist_id>` - Update playlist
- `DELETE /api/playlists/<playlist_id>` - Delete playlist
- `POST /api/playlists/<playlist_id>/songs` - Add song to playlist
- `DELETE /api/playlists/<playlist_id>/songs/<video_id>` - Remove song from playlist

### User Endpoints
- `POST /api/auth/register` - Register new user
- `POST /api/auth/login` - Login user
- `GET /api/users/<user_id>` - Get user profile
- `GET /api/users/<user_id>/liked` - Get liked songs
- `POST /api/users/<user_id>/liked` - Add liked song
- `DELETE /api/users/<user_id>/liked/<video_id>` - Remove liked song
- `GET /api/users/<user_id>/recent` - Get recently played
- `POST /api/users/<user_id>/recent` - Add to recently played

## 🎨 UI Components

### Reusable Components
- **Button**: Multiple variants (primary, secondary, outline, ghost, danger)
- **Card**: Glass-morphism card with hover effects
- **Loading**: Spinner with full-screen option
- **Skeleton**: Loading placeholders

### Music Components
- **SongCard**: Display song with thumbnail, title, artist
- **PlaylistTile**: Playlist preview card
- **SongList**: List of songs with loading states

### Player Components
- **MiniPlayer**: Sticky bottom player with controls
- **FullPlayer**: Full-screen immersive player

## 🔧 Configuration

### Backend Configuration

Edit `config.py` to customize:
- CORS origins
- Secret keys
- yt-dlp options

### Frontend Configuration

Edit `vite.config.js` to customize:
- API proxy settings
- Port configuration

Environment variables (create `.env` in frontend directory):
```
VITE_API_URL=http://localhost:5000/api
```

## 🎯 Features in Detail

### Player Features
- Play/Pause controls
- Next/Previous song navigation
- Volume control
- Progress bar with seek functionality
- Repeat modes: Off, All, One
- Shuffle mode
- Queue management

### Playlist Features
- Create custom playlists
- Add/remove songs
- Edit playlist metadata
- Delete playlists
- Play entire playlists

### User Features
- User registration and login
- Liked songs collection
- Recently played history
- User profile management

## 🛠️ Development

### Project Structure Best Practices

- **Services Layer**: All business logic separated from routes
- **Component-Based**: Reusable React components
- **Context API**: Global state management
- **Error Boundaries**: Graceful error handling
- **Loading States**: Better UX with loading indicators

### Adding New Features

1. **Backend**: Add new service methods in `services/` directory
2. **Frontend**: Create components in `components/` directory
3. **API Integration**: Add service methods in `services/` directory
4. **Routes**: Add routes in `App.jsx`

## 🚨 Known Limitations

1. **Data Persistence**: Currently uses JSON files. For production, use a database.
2. **Authentication**: Basic authentication. Implement JWT for production.
3. **File Storage**: Playlist cover images need cloud storage (S3, Cloudinary).
4. **Caching**: No caching layer. Consider Redis for production.
5. **Rate Limiting**: No rate limiting. Add rate limiting for production.

## 🔐 Security Considerations

For production deployment:
- Use environment variables for secrets
- Implement JWT authentication
- Add rate limiting
- Use HTTPS
- Validate and sanitize all inputs
- Implement CSRF protection
- Use a production database

## 📦 Deployment

### Backend Deployment
1. Use a production WSGI server (Gunicorn, uWSGI)
2. Set up reverse proxy (Nginx)
3. Use environment variables for configuration
4. Set up proper logging
5. Use a production database

### Frontend Deployment
1. Build production bundle: `npm run build`
2. Serve static files with Nginx or CDN
3. Configure environment variables
4. Set up proper caching headers

## 🎓 Learning Resources

- [Flask Documentation](https://flask.palletsprojects.com/)
- [React Documentation](https://react.dev/)
- [Framer Motion](https://www.framer.com/motion/)
- [Tailwind CSS](https://tailwindcss.com/)

## 📝 License

This project is for educational purposes. Please ensure you comply with YouTube's Terms of Service when using this application.

## 🤝 Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## 📧 Support

For issues and questions, please open an issue on the repository.

---

**Built with ❤️ using Flask, React, and modern web technologies**

#   M a j o r - P r o j e c t 
 
 #   M a j o r - P r o j e c t 
 
 #   M a j o r - P r o j e c t 
 
 