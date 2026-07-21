import {
  CommandPaletteProvider,
  CommandPaletteTrigger,
} from "@components/command-palette";
import { LunoLogo } from "@components/luno-logo";
import { PlayStoreButton } from "@components/play-store-button";
import {
  DATA_DELETION_URL,
  GITHUB_URL,
  PRIVACY_URL,
  TERMS_URL,
} from "@components/site";
import type { Metadata } from "next";
import { Footer, Layout, Navbar } from "nextra-theme-docs";
import { Head } from "nextra/components";
import { getPageMap } from "nextra/page-map";
import type { FC, ReactNode } from "react";
import "./globals.css";

export const metadata: Metadata = {
  description:
    "Turn an Android device into a secure, self-hosted communication node. Luno is an open-source SMS gateway and communication agent platform.",
  metadataBase: new URL("https://oss.nexneotech.com/luno"),
  keywords: [
    "Luno",
    "SMS gateway",
    "Android",
    "self-hosted",
    "communication platform",
    "SMS API",
    "open source",
  ],
  generator: "Next.js",
  applicationName: "Luno",
  appleWebApp: {
    title: "Luno",
  },
  title: {
    default: "Luno — Self-hosted SMS gateway for Android",
    template: "%s | Luno",
  },
  openGraph: {
    // https://github.com/vercel/next.js/discussions/50189#discussioncomment-10826632
    url: "./",
    siteName: "Luno",
    locale: "en_US",
    type: "website",
  },
  other: {
    "msapplication-TileColor": "#fff",
  },
  alternates: {
    // https://github.com/vercel/next.js/discussions/50189#discussioncomment-10826632
    canonical: "./",
  },
};

const navbar = (
  <Navbar logo={<LunoLogo height="24" />} projectLink={GITHUB_URL}>
    <PlayStoreButton />
  </Navbar>
);

const footer = (
  <Footer className="flex-col items-center md:items-start">
    <p className="text-xs">Luno is open source, licensed under Apache-2.0.</p>
    <nav className="mt-2 flex gap-4 text-xs">
      <a href={PRIVACY_URL}>Privacy</a>
      <a href={TERMS_URL}>Terms</a>
      <a href={DATA_DELETION_URL}>Data deletion</a>
    </nav>
    <p className="mt-2 text-xs">
      © {new Date().getFullYear()} The Luno Project.
    </p>
  </Footer>
);

const RootLayout: FC<{ children: ReactNode }> = async ({ children }) => {
  const pageMap = await getPageMap();
  return (
    <html lang="en" dir="ltr" suppressHydrationWarning>
      <Head />
      <body>
        <CommandPaletteProvider>
          <Layout
            navbar={navbar}
            pageMap={pageMap}
            docsRepositoryBase={`${GITHUB_URL}/tree/main/web`}
            editLink="Edit this page on GitHub"
            sidebar={{ defaultMenuCollapseLevel: 1 }}
            search={<CommandPaletteTrigger />}
            nextThemes={{ defaultTheme: "system" }}
            footer={footer}
          >
            {children}
          </Layout>
        </CommandPaletteProvider>
      </body>
    </html>
  );
};

export default RootLayout;
