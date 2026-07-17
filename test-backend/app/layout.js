import './globals.css';

export const metadata = {
  title: 'Luno — Test Backend',
  description: 'Reference Luno-protocol backend: pair and drive the Android SMS gateway node live.',
};

export default function RootLayout({ children }) {
  return (
    <html lang="en">
      <body>{children}</body>
    </html>
  );
}
