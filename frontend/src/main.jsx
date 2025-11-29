import React from 'react'
import ReactDOM from 'react-dom/client'
import App from './App'
import './index.css'
import { ErrorBoundary } from 'react-error-boundary'
import { Toaster } from 'react-hot-toast'

function ErrorFallback({error, resetErrorBoundary}) {
  return (
    <div role="alert" className="min-h-screen flex items-center justify-center bg-gradient-to-br from-dark-100 to-dark-50 text-white">
      <div className="text-center p-8">
        <h2 className="text-2xl font-bold mb-4">Something went wrong:</h2>
        <pre className="text-red-400 mb-4">{error.message}</pre>
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

