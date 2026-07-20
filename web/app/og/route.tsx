/* eslint react/no-unknown-property: ['error', { ignore: ['tw'] }] */
import { ImageResponse } from 'next/og'

export const runtime = 'edge'

// eslint-disable-next-line unicorn/prefer-top-level-await -- this will break og image
const font = fetch(new URL('Inter-SemiBold.otf', import.meta.url)).then(res =>
  res.arrayBuffer()
)

export async function GET(req: Request): Promise<Response> {
  try {
    const { searchParams } = new URL(req.url)

    // ?title=<title>
    const title = searchParams.get('title')?.slice(0, 75) || 'Luno Documentation'

    return new ImageResponse(
      <div
        tw="text-white px-20 py-[70px] bg-[#030303] h-full w-full flex justify-between flex-col"
        style={{
          backgroundImage:
            'radial-gradient(circle at 25px 25px, #333 2%, transparent 0%), radial-gradient(circle at 75px 75px, #333 2%, transparent 0%)',
          backgroundSize: '100px 100px',
          backgroundPosition: '-30px -10px'
        }}
      >
        <div tw="flex items-center">
          <svg width="44" height="44" viewBox="0 0 32 32" fill="none">
            <rect
              x="11"
              y="4"
              width="10"
              height="24"
              rx="3"
              stroke="#fff"
              strokeWidth="2"
            />
            <circle cx="16" cy="22" r="1.75" fill="#fff" />
            <path
              d="M6.5 11.5a8 8 0 0 0 0 9M25.5 11.5a8 8 0 0 1 0 9"
              stroke="#999"
              strokeWidth="2"
              strokeLinecap="round"
            />
            <path
              d="M2.5 8.5a13 13 0 0 0 0 15M29.5 8.5a13 13 0 0 1 0 15"
              stroke="#555"
              strokeWidth="2"
              strokeLinecap="round"
            />
          </svg>
          <span tw="ml-3 text-4xl font-bold tracking-tight">Luno</span>
        </div>
        <h1
          tw="text-transparent text-[82px] m-0 mb-10 tracking-tighter leading-[1.1]"
          style={{
            textShadow: '0 2px 30px #000',
            backgroundImage: 'linear-gradient(90deg, #fff 40%, #aaa)',
            backgroundClip: 'text',
            // To preserve new line
            whiteSpace: 'pre'
          }}
        >
          {title}
        </h1>
        <p tw="m-0 text-3xl tracking-tight">
          Self-hosted SMS gateway and communication agent platform.
        </p>
      </div>,
      {
        width: 1200,
        height: 630,
        fonts: [
          {
            name: 'inter',
            data: await font,
            style: 'normal'
          }
        ]
      }
    )
  } catch (error) {
    console.error(error)
    return new Response('Failed to generate the image', { status: 500 })
  }
}
