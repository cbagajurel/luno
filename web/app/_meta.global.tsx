import type { MetaRecord } from 'nextra'

const GETTING_STARTED: MetaRecord = {
  index: 'Overview',
  quickstart: '',
  installation: '',
  pairing: '',
  'first-message': ''
}

const CONCEPTS: MetaRecord = {
  index: 'Overview',
  architecture: '',
  'device-lifecycle': '',
  reliability: '',
  'folder-structure': ''
}

const ANDROID: MetaRecord = {
  index: 'Overview',
  permissions: '',
  'play-protect': '',
  'oem-reliability': ''
}

const MESSAGING: MetaRecord = {
  index: 'Overview',
  'sending-sms': '',
  'receiving-sms': '',
  'delivery-reports': ''
}

const PROTOCOL: MetaRecord = {
  index: 'Overview',
  envelope: '',
  commands: '',
  events: '',
  websocket: '',
  'rest-api': '',
  versioning: ''
}

const ADAPTERS = {
  index: 'Overview',
  hono: 'Hono',
  express: 'Express',
  fastify: 'Fastify',
  nestjs: 'NestJS',
  cloudflare: 'Cloudflare Workers',
  nextjs: 'Next.js',
  firebase: 'Firebase',
  custom: 'Writing your own'
}

const BACKEND_SDK = {
  index: 'Overview',
  core: '',
  adapters: { title: 'Adapters', items: ADAPTERS },
  stores: '',
  testing: ''
}

const SECURITY: MetaRecord = {
  index: 'Overview',
  authentication: '',
  hardening: ''
}

const OPERATIONS: MetaRecord = {
  index: 'Overview',
  'device-management': '',
  troubleshooting: ''
}

export default {
  index: {
    type: 'page',
    display: 'hidden'
  },
  docs: {
    type: 'page',
    title: 'Documentation',
    items: {
      index: 'Introduction',
      _: {
        type: 'separator',
        title: 'Start here'
      },
      'getting-started': { title: 'Getting Started', items: GETTING_STARTED },
      concepts: { title: 'Core Concepts', items: CONCEPTS },
      __: {
        type: 'separator',
        title: 'The node'
      },
      android: { title: 'Android App', items: ANDROID },
      messaging: { title: 'Messaging', items: MESSAGING },
      ___: {
        type: 'separator',
        title: 'The server'
      },
      protocol: { title: 'Protocol', items: PROTOCOL },
      'backend-sdk': { title: 'Backend SDK', items: BACKEND_SDK },
      ____: {
        type: 'separator',
        title: 'Operating a fleet'
      },
      security: { title: 'Security', items: SECURITY },
      operations: { title: 'Operations', items: OPERATIONS },
      _____: {
        type: 'separator',
        title: 'Project'
      },
      roadmap: '',
      contributing: '',
      faq: 'FAQ'
    }
  }
}
