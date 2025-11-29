import React from 'react'
import ReactDOM from 'react-dom/client'
import App from './App'
import './index.css'
import { ErrorBoundary } from 'react-error-boundary'
import { Toaster } from 'react-hot-toast'

/**
 * Error Fallback Component
 * Displays a friendly error UI and logs detailed error information for debugging
 * @param {Object} props
 * @param {Error} props.error - The error that was thrown
 * @param {Function} props.resetErrorBoundary - Function to reset the error boundary
 */
function ErrorFallback({error, resetErrorBoundary}) {
  // Log detailed error information for debugging
  React.useEffect(() => {
    console.error('Error caught by boundary:', {
      message: error.message,
      stack: error.stack,
      name: error.name,
      componentStack: error.componentStack,
      timestamp: new Date().toISOString(),
    })
  }, [error])

  return (
    <div role="alert" className="min-h-screen flex items-center justify-center bg-gradient-to-br from-dark-100 to-dark-50 text-white">
      <div className="text-center p-8 max-w-md">
        <h2 className="text-2xl font-bold mb-4">Something went wrong</h2>
        <pre className="text-red-400 mb-4 text-sm break-words whitespace-pre-wrap bg-dark-200 p-4 rounded-lg">
          {error.message}
        </pre>
        <p className="text-dark-400 text-sm mb-6">
          Check the browser console for more details. If this persists, please refresh the page.
        </p>
        <button
          onClick={resetErrorBoundary}
          className="px-6 py-3 bg-primary-600 hover:bg-primary-700 rounded-lg transition-colors"
        >
          Try again
        </button>
      </div>
    </div>
  )
}

ReactDOM.createRoot(document.getElementById('root')).render(
  <React.StrictMode>
    <ErrorBoundary FallbackComponent={ErrorFallback}>
      <App />
      <Toaster
        position="bottom-right"
        toastOptions={{
          duration: 3000,
          style: {
            background: '#27272a',
            color: '#fff',
            borderRadius: '12px',
          },
        }}
      />
    </ErrorBoundary>
  </React.StrictMode>,
)

