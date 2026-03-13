import { Card } from "@/components/ui/card";

type PageHeaderProps = {
  eyebrow: string;
  title: string;
  description: string;
};

export function PageHeader({ eyebrow, title, description }: PageHeaderProps) {
  return (
    <Card className="space-y-2 p-4 sm:p-6">
      <p className="text-xs font-semibold uppercase tracking-wide text-blue-600 dark:text-blue-400">
        {eyebrow}
      </p>
      <h1 className="text-2xl font-semibold sm:text-3xl">{title}</h1>
      <p className="text-sm text-foreground/75">{description}</p>
    </Card>
  );
}
