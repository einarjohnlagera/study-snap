import Image from "next/image";
import { cn } from "@/lib/utils";

type ProductScreenshotFrameProps = {
  src: string;
  alt: string;
  priority?: boolean;
  sizes?: string;
  className?: string;
  imageClassName?: string;
};

export function ProductScreenshotFrame({
  src,
  alt,
  priority = false,
  sizes = "(min-width: 1280px) 560px, (min-width: 768px) 50vw, 100vw",
  className,
  imageClassName,
}: Readonly<ProductScreenshotFrameProps>) {
  return (
    <div className={cn("overflow-hidden rounded-2xl border border-border/80 bg-background shadow-lg transition-transform duration-200 ease-out motion-safe:hover:scale-[1.02]", className)}>
      <Image
        src={src}
        alt={alt}
        width={1440}
        height={960}
        priority={priority}
        className={cn("h-auto w-full", imageClassName)}
        sizes={sizes}
      />
    </div>
  );
}
