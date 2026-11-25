type IconName = 'overview' | 'api' | 'refresh' | 'book' | 'check' | 'search' | 'code'
const paths: Record<IconName, string> = {
  overview: 'M3 3h7v7H3z M14 3h7v7h-7z M3 14h7v7H3z M14 14h7v7h-7z',
  api: 'm8 7-5 5 5 5 M16 7l5 5-5 5 M14 4l-4 16',
  refresh: 'M20 7v5h-5 M4 17v-5h5 M5.5 7a7.5 7.5 0 0 1 12.4-1L20 9 M4 15l2.1 3A7.5 7.5 0 0 0 18.5 17',
  book: 'M4 3h12a2 2 0 0 1 2 2v16H6a2 2 0 0 1-2-2V3 M4 17h14 M8 7h6 M8 11h6',
  check: 'm5 12 4 4L19 6',
  search: 'M15 15l5 5 M17 10a7 7 0 1 1-14 0 7 7 0 0 1 14 0',
  code: 'M4 5h16v14H4z M8 9l3 3-3 3 M13 15h3',
}
export function Icon({ name, size = 18 }: { name: IconName; size?: number }) {
  return <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d={paths[name]} /></svg>
}
