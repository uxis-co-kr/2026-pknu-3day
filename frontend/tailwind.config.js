import animate from 'tailwindcss-animate'

/** @type {import('tailwindcss').Config} */
export default {
  // 다크 모드는 디자인 브리프 5. "하지 말 것" 에 따라 쓰지 않는다.
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        border: 'hsl(var(--border))',
        input: 'hsl(var(--input))',
        ring: 'hsl(var(--ring))',
        background: 'hsl(var(--background))',
        foreground: 'hsl(var(--foreground))',
        primary: {
          DEFAULT: 'hsl(var(--primary))',
          foreground: 'hsl(var(--primary-foreground))',
        },
        secondary: {
          DEFAULT: 'hsl(var(--secondary))',
          foreground: 'hsl(var(--secondary-foreground))',
        },
        destructive: {
          DEFAULT: 'hsl(var(--destructive))',
          foreground: 'hsl(var(--destructive-foreground))',
        },
        muted: {
          DEFAULT: 'hsl(var(--muted))',
          foreground: 'hsl(var(--muted-foreground))',
        },
        accent: {
          DEFAULT: 'hsl(var(--accent))',
          foreground: 'hsl(var(--accent-foreground))',
        },
        popover: {
          DEFAULT: 'hsl(var(--popover))',
          foreground: 'hsl(var(--popover-foreground))',
        },
        card: {
          DEFAULT: 'hsl(var(--card))',
          foreground: 'hsl(var(--card-foreground))',
        },
        // 디자인 브리프 2. 상태 배지 의미색 — 이 넷 말고 다른 의미색은 쓰지 않는다.
        status: {
          draft: 'hsl(var(--status-draft))',
          confirmed: 'hsl(var(--status-confirmed))',
          failed: 'hsl(var(--status-failed))',
          uncommitted: 'hsl(var(--status-uncommitted))',
        },
      },
      borderRadius: {
        lg: 'var(--radius)',
        md: 'calc(var(--radius) - 2px)',
        sm: 'calc(var(--radius) - 4px)',
      },
      fontFamily: {
        sans: ['Pretendard', 'Pretendard Variable', '-apple-system', 'BlinkMacSystemFont',
               'system-ui', 'Segoe UI', 'Apple SD Gothic Neo', 'Noto Sans KR', 'sans-serif'],
      },
      fontSize: {
        // 디자인 브리프 2. 본문 14px, 표 13px
        base: ['0.875rem', { lineHeight: '1.25rem' }],
        table: ['0.8125rem', { lineHeight: '1.125rem' }],
      },
      keyframes: {
        'accordion-down': { from: { height: '0' }, to: { height: 'var(--radix-accordion-content-height)' } },
        'accordion-up': { from: { height: 'var(--radix-accordion-content-height)' }, to: { height: '0' } },
      },
      animation: {
        'accordion-down': 'accordion-down 0.2s ease-out',
        'accordion-up': 'accordion-up 0.2s ease-out',
      },
    },
  },
  plugins: [animate],
}
