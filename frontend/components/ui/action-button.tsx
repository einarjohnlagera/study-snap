import Link from "next/link";
import type { LinkProps } from "next/link";
import type { LucideIcon } from "lucide-react";
import {
  ArrowLeft,
  ArrowUpRight,
  BookOpen,
  Brain,
  Copy,
  Globe,
  House,
  Link2,
  LogOut,
  Pencil,
  Plus,
  RotateCcw,
  Save,
  Settings,
  Shield,
  Sparkles,
  Trash2,
  User,
  Lock,
} from "lucide-react";
import * as React from "react";
import { Button, buttonVariants } from "@/components/ui/button";
import { cn } from "@/lib/utils";

export type ActionIconName =
  | "back"
  | "copy"
  | "create"
  | "dashboard"
  | "delete"
  | "edit"
  | "library"
  | "admin"
  | "open"
  | "private"
  | "profile"
  | "publicLibrary"
  | "public"
  | "quiz"
  | "retry"
  | "save"
  | "settings"
  | "share"
  | "signOut"
  | "studyPack";

const ACTION_ICONS: Record<ActionIconName, LucideIcon> = {
  back: ArrowLeft,
  copy: Copy,
  create: Plus,
  dashboard: House,
  delete: Trash2,
  edit: Pencil,
  library: BookOpen,
  admin: Shield,
  open: ArrowUpRight,
  private: Lock,
  profile: User,
  publicLibrary: Globe,
  public: Globe,
  quiz: Brain,
  retry: RotateCcw,
  save: Save,
  settings: Settings,
  share: Link2,
  signOut: LogOut,
  studyPack: Sparkles,
};

type ResponsiveActionContentProps = {
  action: ActionIconName;
  label: string;
  showTextOnMobile?: boolean;
  className?: string;
  iconClassName?: string;
};

export function ResponsiveActionContent({
  action,
  label,
  showTextOnMobile = false,
  className,
  iconClassName,
}: Readonly<ResponsiveActionContentProps>) {
  const Icon = ACTION_ICONS[action];

  return (
    <span className={cn("inline-flex items-center justify-center gap-2", className)}>
      <Icon className={cn("h-4 w-4 shrink-0", iconClassName)} aria-hidden="true" />
      <span className={showTextOnMobile ? "inline" : "hidden sm:inline"}>{label}</span>
    </span>
  );
}

type ResponsiveActionButtonProps = React.ComponentProps<typeof Button> & {
  action: ActionIconName;
  label: string;
  showTextOnMobile?: boolean;
};

export function ResponsiveActionButton({
  action,
  label,
  showTextOnMobile = false,
  className,
  children,
  ...props
}: Readonly<ResponsiveActionButtonProps>) {
  return (
    <Button
      aria-label={props["aria-label"] ?? label}
      className={cn(!showTextOnMobile ? "px-3 sm:px-4" : "", className)}
      {...props}
    >
      {children ?? (
        <ResponsiveActionContent action={action} label={label} showTextOnMobile={showTextOnMobile} />
      )}
    </Button>
  );
}

type ResponsiveActionLinkProps = Omit<React.AnchorHTMLAttributes<HTMLAnchorElement>, "href"> & LinkProps & {
  action: ActionIconName;
  label: string;
  variant?: "default" | "outline";
  size?: "default" | "sm";
  showTextOnMobile?: boolean;
};

export function ResponsiveActionLink({
  action,
  label,
  variant = "default",
  size = "default",
  showTextOnMobile = false,
  className,
  children,
  ...props
}: Readonly<ResponsiveActionLinkProps>) {
  return (
    <Link
      aria-label={props["aria-label"] ?? label}
      className={buttonVariants({
        variant,
        size,
        className: cn(!showTextOnMobile ? "px-3 sm:px-4" : "", className),
      })}
      {...props}
    >
      {children ?? (
        <ResponsiveActionContent action={action} label={label} showTextOnMobile={showTextOnMobile} />
      )}
    </Link>
  );
}
