module.exports = {
  root: true,
  env: {
    browser: true,
    es2020: true,
    node: true,
  },
  extends: [
    'eslint:recommended',
    'plugin:react/recommended',
    'plugin:react-hooks/recommended',
  ],
  parserOptions: {
    ecmaVersion: 'latest',
    sourceType: 'module',
    ecmaFeatures: {
      jsx: true,
    },
  },
  plugins: ['react', 'react-hooks'],
  settings: {
    react: {
      version: 'detect',
    },
  },
  rules: {
    // Prevent using undefined variables
    'no-undef': 'error',
    
    // Prevent unused variables
    'no-unused-vars': ['warn', {
      argsIgnorePattern: '^_',
      varsIgnorePattern: '^_',
    }],
    
    // React specific rules
    'react/prop-types': 'off', // We use JSDoc instead
    'react/react-in-jsx-scope': 'off', // Not needed in React 17+
    
    // Hooks rules
    'react-hooks/rules-of-hooks': 'error',
    'react-hooks/exhaustive-deps': 'warn',
    
    // Warn about console statements (but allow console.error)
    'no-console': ['warn', {
      allow: ['warn', 'error'],
    }],
  },
  overrides: [
    {
      // Allow console in development
      files: ['*.js', '*.jsx'],
      env: {
        browser: true,
      },
      rules: {
        'no-console': process.env.NODE_ENV === 'production' 
          ? ['warn', { allow: ['warn', 'error'] }]
          : 'off',
      },
    },
  ],
}

