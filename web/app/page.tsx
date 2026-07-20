import { PLAY_STORE_URL } from '@components/site'
import type { Metadata } from 'next'
import { Link } from 'nextra-theme-docs'
import { Feature, Features } from './_components/features'
import styles from './page.module.css'
import './page.css'
import type { FC } from 'react'

export const metadata: Metadata = {
  description:
    'Luno turns an Android device into a secure, self-hosted communication node. Send and receive SMS through your own backend over a versioned, real-time protocol — no third-party gateway, no per-message pricing.'
}

const IndexPage: FC = () => {
  return (
    <div className="home-content">
      <div className="content-container">
        <h1 className="headline">
          Your phone is the gateway. <br className="max-sm:hidden" />
          Your server is in charge.
        </h1>
        <p className="subtitle">
          Luno turns an Android device into a secure, self-hosted communication
          node <br className="max-md:hidden" />
          you drive from your own backend — over a versioned, real-time
          protocol.
        </p>
        <p className="subtitle flex flex-wrap items-center gap-3">
          <Link className={styles.cta} href="/docs">
            Get started <span>→</span>
          </Link>
          <a
            className={styles.ctaSecondary}
            href={PLAY_STORE_URL}
            target="_blank"
            rel="noreferrer"
          >
            Get it on Google Play
          </a>
        </p>
      </div>
      <div className="features-container x:border-b nextra-border">
        <div className="content-container">
          <Features>
            <Feature
              index={0}
              large
              centered
              id="node-card"
              href="/docs/concepts/architecture"
            >
              <h3>
                A headless agent, <br className="show-on-mobile" />
                not an app with a socket
              </h3>
              <p className="mb-8">
                The entire gateway is a native Android foreground service:
                queue, socket, telephony, retries and crypto. Kill the UI and it
                keeps running — that is the acceptance test for the boundary.
              </p>
            </Feature>
            <Feature index={1} centered href="/docs/protocol">
              <h3>
                One versioned <br className="show-on-mobile" />
                wire <span className="font-light">protocol</span>
              </h3>
              <p className="mb-8 text-start">
                The node prescribes no server technology. It depends only on a
                published JSON envelope, so any backend that speaks it — in any
                language — can drive a fleet.
              </p>
              <div>
                <div className={styles.optimization}>
                  <div style={{ fontSize: '.9rem' }} className="leading-8">
                    <code>{'{ "kind": "command",'}</code>
                    <br />
                    <code>{'  "type": "send_sms" }'}</code>
                  </div>
                </div>
              </div>
            </Feature>
            <Feature index={2} id="protocol-card" href="/docs/messaging">
              <h3>
                Persist first, <br className="show-on-mobile" />
                then touch the radio
              </h3>
              <p>
                Every command is written to a durable Room outbox before an ack
                is sent, and every inbound message before it is reported. The
                rest is a state machine draining that store.
              </p>
            </Feature>
            <Feature index={3} id="offline-card" href="/docs/concepts/reliability">
              <h3>
                Designed to be killed, <br className="show-on-mobile" />
                and to come back
              </h3>
              <p>
                Android and OEM skins will kill your process. Luno plans for
                recovery instead of immortality: boot receiver, WorkManager
                backstop, and a resync handshake that makes reconnection
                lossless and duplicate-free.
              </p>
            </Feature>
            <Feature
              index={4}
              centered
              className="feat-darkmode flex items-center justify-center"
            >
              <h3>
                Self <br />
                hosted <br />
                by default
              </h3>
            </Feature>
            <Feature index={5} large id="security-card" href="/docs/security">
              <h3>
                Credentials that are
                <br />
                useless off the device
              </h3>
              <p className="z-2">
                The device credential is sealed by the Android Keystore, message
                bodies and phone numbers are encrypted at rest, and a single
                central redactor keeps PII out of every log line. Rate limits
                are backend-authoritative but enforced client-side too, so a
                compromised server still cannot turn a SIM into a spam cannon.
              </p>
            </Feature>
            <Feature index={6} large id="sdk-card" href="/docs/backend-sdk">
              <h3>
                Bring your own backend, <br />
                or install one in five lines
              </h3>
              <p>
                The <code>@luno-oss/*</code> SDK packages implement the server half
                for you — a framework-independent core plus thin adapters for
                Hono, Express, Fastify, NestJS and Cloudflare Workers.
              </p>
            </Feature>
            <Feature index={7} href="/docs/messaging/delivery-reports">
              <h3>
                Multi-SIM and <br />
                delivery-report aware
              </h3>
              <p className="mr-6">
                Messages fan out to parts, each part carries its own correlation
                id, and rollup is time-bounded — so a carrier that never sends a
                report degrades to <code>UNDELIVERED</code> instead of hanging
                forever.
              </p>
            </Feature>
            <Feature index={8} large>
              <h3>And it is only the first transport.</h3>
              <p>
                SMS is what v1 ships. The <code>Transport</code> interface and
                the wire protocol are both transport-neutral, so MMS, USSD and
                non-Android nodes slot in without reshaping the queue, the
                protocol or the UI.
              </p>
              <p className="subtitle">
                <Link className="no-underline" href="/docs">
                  Read the documentation →
                </Link>
              </p>
            </Feature>
          </Features>
        </div>
      </div>
    </div>
  )
}

export default IndexPage
