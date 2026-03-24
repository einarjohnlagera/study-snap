type StructuredDataScriptProps = {
  id: string;
  data: Record<string, unknown>;
};

export function StructuredDataScript({ id, data }: StructuredDataScriptProps) {
  return (
    <script
      id={id}
      type="application/ld+json"
      dangerouslySetInnerHTML={{ __html: JSON.stringify(data) }}
    />
  );
}
