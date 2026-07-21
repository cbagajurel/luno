"use client";

import {
  BookOpenIcon,
  FileTextIcon,
  LaptopIcon,
  MoonIcon,
  SearchIcon,
  SunIcon,
} from "lucide-react";
import { addBasePath } from "next/dist/client/add-base-path";
import { useRouter } from "next/navigation";
import { useTheme } from "next-themes";
import {
  createContext,
  useCallback,
  useContext,
  useDeferredValue,
  useEffect,
  useMemo,
  useState,
  type FC,
  type ReactNode,
} from "react";
import {
  CommandDialog,
  CommandEmpty,
  CommandGroup,
  CommandInput,
  CommandItem,
  CommandList,
  CommandSeparator,
  CommandShortcut,
} from "./ui/command";

export interface DocPage {
  title: string;
  route: string;
  section: string;
}

interface PagefindResult {
  id: string;
  url: string;
  meta: { title: string };
  excerpt: string;
}

interface Pagefind {
  options(opts: Record<string, unknown>): Promise<void>;
  debouncedSearch(
    query: string,
    options?: unknown,
    debounceMs?: number,
  ): Promise<{ results: { data(): Promise<PagefindResult> }[] } | null>;
}

declare global {
  // eslint-disable-next-line no-var
  var pagefind: Pagefind | undefined;
}

async function loadPagefind(): Promise<Pagefind> {
  if (globalThis.pagefind) return globalThis.pagefind;
  const mod: Pagefind = await import(
    /* webpackIgnore: true */
    addBasePath("/_pagefind/pagefind.js")
  );
  await mod.options({ baseUrl: "/" });
  globalThis.pagefind = mod;
  return mod;
}

const MAX_PAGE_HITS = 6;
const MAX_CONTENT_HITS = 8;

function scorePage(page: DocPage, query: string): number {
  const title = page.title.toLowerCase();
  const section = page.section.toLowerCase();
  if (title === query) return 100;
  if (title.startsWith(query)) return 80;
  if (title.includes(query)) return 60;
  if (section.includes(query)) return 30;
  if (page.route.includes(query)) return 20;
  return 0;
}

const CommandPaletteContext = createContext<(() => void) | null>(null);

// nextra renders the `search` slot twice (desktop navbar + mobile sidebar),
// so the trigger button below is mounted in two places at once. The dialog,
// its state, and its ⌘K listener must stay singletons here — otherwise both
// instances open on ⌘K and closing one leaves the other stacked on top.
export const CommandPaletteProvider: FC<{
  children: ReactNode;
}> = ({ children }) => {
  const [pages, setPages] = useState<DocPage[]>([]);
  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState("");
  const [results, setResults] = useState<PagefindResult[]>([]);
  const [error, setError] = useState("");
  const deferredQuery = useDeferredValue(query);
  const router = useRouter();
  const { setTheme } = useTheme();

  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === "k" && (event.metaKey || event.ctrlKey)) {
        event.preventDefault();
        setOpen((o) => !o);
      }
    };
    document.addEventListener("keydown", onKeyDown);
    return () => document.removeEventListener("keydown", onKeyDown);
  }, []);

  // Fetched on first open rather than passed down from the layout, so the page
  // list stays out of every route's RSC payload. See app/doc-pages/route.ts.
  useEffect(() => {
    if (!open || pages.length) return;
    let cancelled = false;
    void (async () => {
      try {
        const response = await fetch(addBasePath("/doc-pages"));
        const data: DocPage[] = await response.json();
        if (!cancelled) setPages(data);
      } catch {
        /* jump-to-page degrades to content search */
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [open, pages.length]);

  useEffect(() => {
    const value = deferredQuery.trim();
    if (!value) {
      setResults([]);
      setError("");
      return;
    }
    let cancelled = false;
    void (async () => {
      try {
        const pagefind = await loadPagefind();
        const response = await pagefind.debouncedSearch(value);
        if (!response || cancelled) return;
        const data = await Promise.all(
          response.results.slice(0, MAX_CONTENT_HITS).map((r) => r.data()),
        );
        if (cancelled) return;
        setResults(data);
        setError("");
      } catch (e) {
        if (cancelled) return;
        setResults([]);
        setError(
          process.env.NODE_ENV === "production"
            ? "Search is unavailable."
            : "Search needs a production build — run `next build`, then restart.",
        );
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [deferredQuery]);

  const run = useCallback((action: () => void) => {
    setOpen(false);
    setQuery("");
    action();
  }, []);

  const openPalette = useMemo(() => () => setOpen(true), []);

  const trimmed = query.trim().toLowerCase();
  const pageHits = trimmed
    ? pages
        .map((page) => ({ page, score: scorePage(page, trimmed) }))
        .filter((hit) => hit.score > 0)
        .sort((a, b) => b.score - a.score)
        .slice(0, MAX_PAGE_HITS)
        .map((hit) => hit.page)
    : [];

  const byRoute = new Map(pages.map((page) => [page.route, page]));
  const suggestions = trimmed
    ? []
    : SUGGESTED.map((route) => byRoute.get(route)).filter((page) => !!page);

  return (
    <CommandPaletteContext.Provider value={openPalette}>
      {children}

      <CommandDialog
        open={open}
        onOpenChange={setOpen}
        label="Search documentation"
        shouldFilter={false}
        loop
      >
        <CommandInput
          value={query}
          onValueChange={setQuery}
          placeholder="Search documentation or jump to a page…"
          autoFocus
        />
        <CommandList>
          {trimmed && !pageHits.length && !results.length && (
            <CommandEmpty>{error || "No results found."}</CommandEmpty>
          )}

          {suggestions.length > 0 && (
            <CommandGroup heading="Suggestions">
              {suggestions.map((page) => (
                <CommandItem
                  key={page.route}
                  value={page.route}
                  onSelect={() => run(() => router.push(page.route))}
                >
                  <BookOpenIcon className="opacity-60 size-4" />
                  <span>{page.title}</span>
                  <span className="opacity-50 ms-auto text-xs">
                    {page.section}
                  </span>
                </CommandItem>
              ))}
            </CommandGroup>
          )}

          {pageHits.length > 0 && (
            <CommandGroup heading="Pages">
              {pageHits.map((page) => (
                <CommandItem
                  key={page.route}
                  value={`page:${page.route}`}
                  onSelect={() => run(() => router.push(page.route))}
                >
                  <BookOpenIcon className="opacity-60 size-4" />
                  <span>{page.title}</span>
                  <span className="opacity-50 ms-auto text-xs">
                    {page.section}
                  </span>
                </CommandItem>
              ))}
            </CommandGroup>
          )}

          {results.length > 0 && (
            <CommandGroup heading="Content">
              {results.map((result) => (
                <CommandItem
                  key={result.id}
                  value={`content:${result.id}`}
                  onSelect={() => run(() => router.push(result.url))}
                  className="items-start"
                >
                  <FileTextIcon className="opacity-60 mt-0.5 size-4 shrink-0" />
                  <span className="min-w-0">
                    <span className="block truncate">{result.meta.title}</span>
                    <span
                      className="block opacity-60 text-xs nextra-cmd-excerpt"
                      // Pagefind returns its own highlight markup
                      dangerouslySetInnerHTML={{ __html: result.excerpt }}
                    />
                  </span>
                </CommandItem>
              ))}
            </CommandGroup>
          )}

          {!trimmed && (
            <>
              <CommandSeparator />
              <CommandGroup heading="Theme">
                <CommandItem
                  value="theme-light"
                  onSelect={() => run(() => setTheme("light"))}
                >
                  <SunIcon className="opacity-60 size-4" />
                  <span>Light</span>
                </CommandItem>
                <CommandItem
                  value="theme-dark"
                  onSelect={() => run(() => setTheme("dark"))}
                >
                  <MoonIcon className="opacity-60 size-4" />
                  <span>Dark</span>
                </CommandItem>
                <CommandItem
                  value="theme-system"
                  onSelect={() => run(() => setTheme("system"))}
                >
                  <LaptopIcon className="opacity-60 size-4" />
                  <span>System</span>
                  <CommandShortcut>default</CommandShortcut>
                </CommandItem>
              </CommandGroup>
            </>
          )}
        </CommandList>
      </CommandDialog>
    </CommandPaletteContext.Provider>
  );
};

export const CommandPaletteTrigger: FC = () => {
  const openPalette = useContext(CommandPaletteContext);
  return (
    <button
      type="button"
      onClick={() => openPalette?.()}
      aria-label="Search documentation"
      className="flex items-center gap-2 px-2.5 border nextra-border rounded-lg w-full md:w-64 h-9 text-sm nextra-cmd-trigger x:focus-visible:nextra-focus"
    >
      <SearchIcon className="opacity-50 size-4 shrink-0" aria-hidden />
      <span className="opacity-60">Search documentation…</span>
      <kbd className="hidden md:block opacity-60 ms-auto px-1.5 border nextra-border rounded font-sans text-[.7rem]">
        ⌘K
      </kbd>
    </button>
  );
};

const SUGGESTED = [
  "/docs",
  "/docs/getting-started/installation",
  "/docs/getting-started/pairing",
  "/docs/concepts/architecture",
  "/docs/protocol",
  "/docs/backend-sdk",
];
