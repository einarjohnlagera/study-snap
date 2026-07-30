import Link from "next/link";
import type { LinkProps } from "next/link";
import type { LucideIcon } from "lucide-react";
import {
  ArrowLeft,
  ArrowUpRight,
  BarChart2,
  Brain,
  BookOpen,
  BookMarked,
  Briefcase,
  Compass,
  Copy,
  Download,
  GraduationCap,
  Globe,
  HelpCircle,
  House,
  Link2,
  Layers,
  ListTree,
  LogOut,
  Mail,
  Pencil,
  Plus,
  RotateCcw,
  Save,
  Settings,
  Shield,
  Sparkles,
  Star,
  Target,
  Trophy,
  Trash2,
  Upload,
  User,
  Lock,
  Zap,
} from "lucide-react";
import * as React from "react";
import { Button, buttonVariants } from "@/components/ui/button";
import { cn } from "@/lib/utils";

export type ActionIconName =
  | "back"
  | "build"
  | "copy"
  | "create"
  | "dashboard"
  | "delete"
  | "download"
  | "edit"
  | "explore"
  | "collections"
  | "library"
  | "admin"
  | "campaigns"
  | "help"
  | "import"
  | "open"
  | "primary"
  | "private"
  | "profile"
  | "progress"
  | "public"
  | "quickReview"
  | "challengeQuiz"
  | "adaptivePractice"
  | "companion"
  | "flashcards"
  | "memorization"
  | "interviewPractice"
  | "retry"
  | "save"
  | "settings"
  | "share"
  | "signOut"
  | "studyPack";

const ACTION_ICONS: Record<ActionIconName, LucideIcon> = {
  back: ArrowLeft,
  build: ListTree,
  copy: Copy,
  create: Plus,
  dashboard: House,
  delete: Trash2,
  download: Download,
  edit: Pencil,
  explore: Compass,
  collections: Layers,
  library: BookOpen,
  admin: Shield,
  campaigns: Mail,
  help: HelpCircle,
  import: Upload,
  open: ArrowUpRight,
  primary: Star,
  private: Lock,
  profile: User,
  progress: BarChart2,
  public: Globe,
  quickReview: Zap,
  challengeQuiz: Trophy,
  adaptivePractice: Target,
  companion: GraduationCap,
  flashcards: BookMarked,
  memorization: Brain,
  interviewPractice: Briefcase,
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
  showTextOnMobile = true,
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
  showTextOnMobile = true,
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
  showTextOnMobile = true,
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
