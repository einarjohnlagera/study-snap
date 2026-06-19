import { Card } from "@/components/ui/card";
import { BrandFullLogo } from "@/components/branding/brand-assets";

type PageHeaderProps = {
  eyebrow: string;
  title: string;
  description?: string;
  meta?: React.ReactNode;
  actions?: React.ReactNode;
  footer?: React.ReactNode;
  brandLogo?: boolean;
};

export function PageHeader({ eyebrow, title, description, meta, actions, footer, brandLogo = false }: Readonly<PageHeaderProps>) {
  return (
    <Card className="space-y-3 p-4 sm:p-6">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
        <div className="space-y-2">
          {brandLogo ? <BrandFullLogo width={176} height={36} priority /> : null}
          <p className="text-xs font-semibold uppercase tracking-wide text-blue-600 dark:text-blue-400">
            {eyebrow}
          </p>
          <h1 className="text-2xl font-semibold sm:text-3xl">{title}</h1>
          {meta ? <div>{meta}</div> : null}
          {description ? <p className="text-sm text-foreground/75">{description}</p> : null}
        </div>
        {actions ? <div className="w-full sm:w-auto">{actions}</div> : null}
      </div>
      {footer ? <div className="pt-1">{footer}</div> : null}
    </Card>
  );
}
