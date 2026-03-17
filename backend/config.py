import os

# Base URL for the backend (used for constructing links, etc.)
BASE_URL = os.getenv("BASE_URL", "http://localhost:5000")

# Port for Flask app
PORT = int(os.getenv("PORT", 5000))

# Add other config variables as needed
