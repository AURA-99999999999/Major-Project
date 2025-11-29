# Integration Instructions

## Step-by-Step Setup Guide

### 1. Backend Setup

#### Initial Setup
```bash
# Navigate to Aura directory
cd DeciBel

# Activate virtual environment (if not already activated)
# Windows:
venv\Scripts\activate
# macOS/Linux:
source venv/bin/activate

# Install dependencies
pip install -r requirements.txt
```

#### Run Backend Server
```bash
python app.py
```

The server should start on `http://localhost:5000`

**Verify Backend:**
- Open browser: `http://localhost:5000/api/health`
- Should return: `{"status": "healthy", "service": "Aura Music API"}`

### 2. Frontend Setup

#### Initial Setup
```bash
# Navigate to frontend directory
cd frontend
#retry

# Install dependencies
npm install
```

#### Run Frontend Server
```bash
npm run dev
```

The apppp should start on `http://localhost:3000`

### 3. Verify Integration

1. **Check Backend API:**
   ```bash
   curl http://localhost:5000/api/health
   ```

2. **Check Frontend:**
   - Open `http://localhost:3000`
   - You should see the Aura home page

3. **Test Search:**
   - Type a song name in the search bar
   - Results should appear

4. **Test Playback:**
   - Click on a song
   - Audio should start playing in the mini player

## Troubleshooting

### Backend Issues

**Port 5000 already in use:**
```python
# Edit app.py, change port:
if __name__ == '__main__':
    app.run(debug=True, port=5001)  # Change to different port
```

**CORS errors:**
- Ensure `flask-cors` is installed: `pip install flask-cors`
- Check `config.py` for CORS origins

**ytmusicapi errors:**
- Ensure you have internet connection
- YouTube Music API may have rate limits

### Frontend Issues

**Port 3000 already in use:**
- Vite will automatically use the next available port
- Or change in `vite.config.js`

**API connection errors:**
- Check backend is running on port 5000
- Verify proxy settings in `vite.config.js`
- Check browser console for errors

**Module not found:**
- Delete `node_modules` and `package-lock.json`
- Run `npm install` again

### Common Issues

1. **Songs not playing:**
   - Check browser console for errors
   - Verify audio URL is accessible
   - Try a different song

2. **Images not loading:**
   - Some thumbnails may not load
   - App has fallback placeholder images

3. **Playlists not saving:**
   - Check `data/` directory exists
   - Ensure write permissions

## Production Deployment

### Backend Deployment

1. **Use Gunicorn:**
   ```bash
   pip install gunicorn
   gunicorn -w 4 -b 0.0.0.0:5000 app:app
   ```

2. **Use environment variables:**
   ```bash
   export SECRET_KEY='your-secret-key'
   export CORS_ORIGINS='https://yourdomain.com'
   ```

3. **Use PostgreSQL/MongoDB** instead of JSON files

### Frontend Deployment

1. **Build production bundle:**
   ```bash
   npm run build
   ```

2. **Serve with Nginx:**
   ```nginx
   server {
       listen 80;
       server_name yourdomain.com;
       
       location / {
           root /path/to/frontend/dist;
           try_files $uri /index.html;
       }
       
       location /api {
           proxy_pass http://localhost:5000;
       }
   }
   ```

## Environment Variables

### Backend (.env)
```env
SECRET_KEY=your-secret-key-here
CORS_ORIGINS=http://localhost:3000,https://yourdomain.com
```

### Frontend (.env)
```env
VITE_API_URL=http://localhost:5000/api
```

## Database Migration (Future)

For production, migrate from JSON to database:

1. **Install SQLAlchemy:**
   ```bash
   pip install flask-sqlalchemy
   ```

2. **Create models in `models/` directory**

3. **Update services to use database instead of JSON**

## Testing

### Backend Testing
```bash
# Install pytest
pip install pytest

# Run tests
pytest
```

### Frontend Testing
```bash
# Install testing libraries
npm install --save-dev @testing-library/react @testing-library/jest-dom

# Run tests
npm test
```

## Performance Optimization

### Backend
- Add Redis caching
- Implement rate limiting
- Use connection pooling
- Add response compression

### Frontend
- Code splitting with React.lazy()
- Image optimization
- Service worker for offline support
- Bundle analysis and optimization

## Security Checklist

- [ ] Use environment variables for secrets
- [ ] Implement JWT authentication
- [ ] Add rate limiting
- [ ] Use HTTPS
- [ ] Validate all inputs
- [ ] Implement CSRF protection
- [ ] Use a production database
- [ ] Set up proper logging
- [ ] Regular dependency updates
- [ ] Security headers configuration

