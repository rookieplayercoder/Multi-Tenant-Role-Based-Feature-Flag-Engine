/** @type {import('tailwindcss').Config} */
export default {
  darkMode: 'class',
  content: ['./index.html', './src/**/*.{js,ts,jsx,tsx}'],
  theme: {
    extend: {
      colors: {
        brand: {
          50: '#eef4ff',
          100: '#dae6ff',
          200: '#b9d0ff',
          300: '#8ab0ff',
          400: '#5a8bff',
          500: '#3466f6',
          600: '#254bda',
          700: '#1f3cb0',
          800: '#1d3690',
          900: '#1c3175',
        },
        surface: {
          50: '#f7f8fa',
          100: '#eef0f3',
          200: '#e2e5ea',
          300: '#cbd0d9',
          400: '#9aa2b1',
          500: '#6b7284',
          600: '#4d5464',
          700: '#333a4a',
          800: '#1b2130',
          900: '#12151f',
        },
        /**
         * Accent for "on"/active states — feature flags are binary
         * switches, so this is the one warm color in an otherwise cool
         * blue+neutral palette. Used sparingly (toggle "on" state, one
         * hero highlight), never as a second neutral.
         */
        ignition: {
          50: '#fff8ec',
          100: '#ffedc7',
          200: '#ffd98a',
          300: '#ffbe4d',
          400: '#ffa41f',
          500: '#f78a09',
          600: '#d66a04',
          700: '#b04d08',
          800: '#8f3d0e',
          900: '#76330f',
        },
      },
      fontFamily: {
        sans: ['Inter', 'ui-sans-serif', 'system-ui', 'sans-serif'],
        display: ['"Space Grotesk"', 'Inter', 'ui-sans-serif', 'sans-serif'],
        mono: ['"JetBrains Mono"', 'ui-monospace', 'SFMono-Regular', 'monospace'],
      },
    },
  },
  plugins: [],
};