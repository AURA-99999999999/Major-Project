import os
from dotenv import load_dotenv

load_dotenv()

# Base URL for the backend (used for constructing links, etc.)
BASE_URL = os.getenv("BASE_URL", "http://localhost:5000")

# Port for Flask app
PORT = int(os.getenv("PORT", 5000))

# Deployment environment
ENV = os.getenv("ENV", "development").strip().lower()

# External call behavior
REQUEST_TIMEOUT_SECONDS = float(os.getenv("REQUEST_TIMEOUT_SECONDS", "5"))

# Add other config variables as needed
