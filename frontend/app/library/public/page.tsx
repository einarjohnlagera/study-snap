import { redirect } from "next/navigation";
import { buildPublicLibraryUrl, parsePublicLibraryFilters } from "@/lib/public-library-url";

type PublicLibraryRedirectPageProps = {
  searchParams?: Promise<Record<string, string | string[] | undefined>>;
};

export default async function PublicLibraryRedirectPage({
  searchParams,
}: Readonly<PublicLibraryRedirectPageProps>) {
  const resolvedSearchParams = searchParams ? await searchParams : undefined;
  redirect(buildPublicLibraryUrl(parsePublicLibraryFilters(resolvedSearchParams), resolvedSearchParams));
}
