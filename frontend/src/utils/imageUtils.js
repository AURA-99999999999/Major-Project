/**
 * Utility functions for handling images
 */

// Generate a data URI for a placeholder image
export const getPlaceholderImage = (size = 200) => {
  const svg = `
    <svg width="${size}" height="${size}" xmlns="http://www.w3.org/2000/svg">
      <rect width="100%" height="100%" fill="#18181b"/>
      <text x="50%" y="50%" font-family="Arial" font-size="${size * 0.2}" 
            fill="#71717a" text-anchor="middle" dominant-baseline="middle">🎵</text>
    </svg>
  `
  return `data:image/svg+xml;base64,${btoa(svg)}`
}

// Handle image loading errors
export const handleImageError = (e, fallbackSize = 200) => {
  e.target.src = getPlaceholderImage(fallbackSize)
  e.target.onerror = null // Prevent infinite loop
}

