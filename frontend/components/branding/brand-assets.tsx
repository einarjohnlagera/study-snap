import Image from "next/image";
import { cn } from "@/lib/utils";

type BrandMonogramProps = {
  size?: number;
  className?: string;
  priority?: boolean;
};

type BrandFullLogoProps = {
  width?: number;
  height?: number;
  className?: string;
  priority?: boolean;
};

type BrandProductIconProps = {
  size?: number;
  className?: string;
  priority?: boolean;
  alt?: string;
};

export function BrandMonogram({
  size = 32,
  className,
  priority = false,
}: Readonly<BrandMonogramProps>) {
  return (
    <Image
      src="/notelib-logo-monogram.png"
      alt="NoteLib"
      width={size}
      height={size}
      priority={priority}
      className={cn("h-auto w-auto", className)}
    />
  );
}

export function BrandFullLogo({
  width = 192,
  height = 40,
  className,
  priority = false,
}: Readonly<BrandFullLogoProps>) {
  return (
    <>
      <Image
        src="/notelib-logo-full-light.svg"
        alt="NoteLib"
        width={width}
        height={height}
        priority={priority}
        className={cn("h-auto w-auto dark:hidden", className)}
      />
      <Image
        src="/notelib-logo-full-dark.svg"
        alt="NoteLib"
        width={width}
        height={height}
        priority={priority}
        className={cn("hidden h-auto w-auto dark:block", className)}
      />
    </>
  );
}

export function BrandProductIcon({
  size = 72,
  className,
  priority = false,
  alt = "NoteLib product icon",
}: Readonly<BrandProductIconProps>) {
  return (
    <Image
      src="/notelib-logo-icon.svg"
      alt={alt}
      width={size}
      height={size}
      priority={priority}
      className={cn("h-auto w-auto", className)}
    />
  );
}
