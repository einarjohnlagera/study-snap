"use client";

import Link from "next/link";
import { ResponsiveActionContent, type ActionIconName } from "@/components/ui/action-button";
import { getCollectionLabels } from "@/lib/collection-labels";
import { cn } from "@/lib/utils";
import type { ProfileType } from "@/lib/api";

type MobileBottomTabBarProps = {
  pathname: string;
  profileType: ProfileType | null;
};

type MobileTab = {
  href: string;
  label: string;
  action: ActionIconName;
};

function isTabActive(pathname: string, href: string): boolean {
  if (href === "/library") {
    return pathname === href;
  }
  return pathname === href || pathname.startsWith(`${href}/`);
}

export function MobileBottomTabBar({ pathname, profileType }: Readonly<MobileBottomTabBarProps>) {
  const collectionLabels = getCollectionLabels(profileType);
  const tabs: MobileTab[] = [
    { href: "/dashboard", label: "Dashboard", action: "dashboard" },
    { href: "/library", label: "Library", action: "library" },
    { href: "/collections", label: collectionLabels.navLabel, action: "collections" },
    { href: "/public/library", label: "Public Library", action: "publicLibrary" },
  ];

  return (
    <nav
      aria-label="Mobile navigation"
      className="fixed inset-x-0 bottom-0 z-20 border-t border-border bg-background/95 backdrop-blur md:hidden"
      data-testid="mobile-bottom-tab-bar"
    >
      <div className="flex min-h-[4.5rem] items-stretch px-1 pb-[env(safe-area-inset-bottom,0px)] pt-1">
        {tabs.map((tab) => {
          const active = isTabActive(pathname, tab.href);
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
