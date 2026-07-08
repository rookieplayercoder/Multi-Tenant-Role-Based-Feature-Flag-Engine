/** @type {import('tailwindcss').Config} */
export default {
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
          800: '#1b2130',
          900: '#12151f',
        },
      },
      fontFamily: {
        sans: ['Inter', 'ui-sans-serif', 'system-ui', 'sans-serif'],
        mono: ['"JetBrains Mono"', 'ui-monospace', 'SFMono-Regular', 'monospace'],
      },
    },
  },
  plugins: [],
};
