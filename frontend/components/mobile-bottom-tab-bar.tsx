"use client";

import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { ResponsiveActionContent, type ActionIconName } from "@/components/ui/action-button";
import { getCollectionLabels } from "@/lib/collection-labels";
import { PUBLIC_LIBRARY_PATH, PUBLIC_LIBRARY_RETURN_URL_STORAGE_KEY } from "@/lib/public-library-url";
import { cn } from "@/lib/utils";
import type { ProfileType } from "@/lib/api";

type MobileBottomTabBarProps = {
  pathname: string;
  profileType: ProfileType | null;
};

type MobileTab = {
  href: string;
  activeHref: string;
  label: string;
  action: ActionIconName;
};

const LIBRARY_PATH = "/library";

function isTabActive(pathname: string, activeHref: string): boolean {
  if (activeHref === LIBRARY_PATH) {
    return pathname === activeHref;
  }
  return pathname === activeHref || pathname.startsWith(`${activeHref}/`);
}

function getPublicLibraryReturnHref(): string {
  try {
    const saved = globalThis.sessionStorage?.getItem(PUBLIC_LIBRARY_RETURN_URL_STORAGE_KEY);
    return saved?.startsWith(PUBLIC_LIBRARY_PATH) ? saved : PUBLIC_LIBRARY_PATH;
  } catch {
    return PUBLIC_LIBRARY_PATH;
  }
}

export function MobileBottomTabBar({ pathname, profileType }: Readonly<MobileBottomTabBarProps>) {
  const searchParams = useSearchParams();
  const collectionLabels = getCollectionLabels(profileType);
  const privateLibraryRef = searchParams.get("ref");
  const libraryHref = privateLibraryRef?.startsWith(LIBRARY_PATH) ? privateLibraryRef : LIBRARY_PATH;
  const publicLibraryHref = getPublicLibraryReturnHref();
  const tabs: MobileTab[] = [
    { href: "/dashboard", activeHref: "/dashboard", label: "Dashboard", action: "dashboard" },
    { href: libraryHref, activeHref: LIBRARY_PATH, label: "Library", action: "library" },
    { href: "/collections", activeHref: "/collections", label: collectionLabels.navLabel, action: "collections" },
    { href: publicLibraryHref, activeHref: PUBLIC_LIBRARY_PATH, label: "Public Library", action: "publicLibrary" },
  ];

  return (
    <nav
      aria-label="Mobile navigation"
      className="fixed inset-x-0 bottom-0 z-20 border-t border-border bg-background/95 backdrop-blur md:hidden"
      data-testid="mobile-bottom-tab-bar"
    >
      <div className="flex min-h-[4.5rem] items-stretch px-1 pb-[env(safe-area-inset-bottom,0px)] pt-1">
        {tabs.map((tab) => {
          const active = isTabActive(pathname, tab.activeHref);
          return (
            <Link
              key={tab.href}
              href={tab.href}
              aria-current={active ? "page" : undefined}
              className={cn(
                "flex min-w-0 flex-1 items-center justify-center rounded-md px-1 py-2 text-center text-[11px] font-medium transition-colors",
                active
                  ? "bg-highlight-strong text-foreground"
                  : "text-foreground/65 hover:bg-highlight hover:text-foreground",
              )}
            >
              <ResponsiveActionContent
                action={tab.action}
                label={tab.label}
                className="flex-col gap-1"
                iconClassName="h-4 w-4"
              />
            </Link>
          );
        })}
      </div>
    </nav>
  );
}
