import { permanentRedirect } from "next/navigation";
import { buildPublicLibraryUrl, parsePublicLibraryFilters } from "@/lib/public-library-url";

type PublicLibrarySubjectRedirectPageProps = {
  params: Promise<{
    subject: string;
  }>;
  searchParams?: Promise<Record<string, string | string[] | undefined>>;
};

export default async function PublicLibrarySubjectRedirectPage({
  params,
  searchParams,
}: Readonly<PublicLibrarySubjectRedirectPageProps>) {
  const [{ subject }, resolvedSearchParams] = await Promise.all([
    params,
    searchParams ?? Promise.resolve(undefined),
  ]);

  const nextFilters = parsePublicLibraryFilters(resolvedSearchParams);
  permanentRedirect(buildPublicLibraryUrl({
    ...nextFilters,
    subject,
  }, resolvedSearchParams));
}
