import { redirect } from "next/navigation";

type SignupPageProps = {
  searchParams: Promise<{
    redirect?: string;
    intent?: string;
  }>;
};

export default async function SignupPage({ searchParams }: Readonly<SignupPageProps>) {
  const { redirect: redirectTarget, intent } = await searchParams;
  const authSearchParams = new URLSearchParams({ mode: "signup" });
  if (redirectTarget) {
    authSearchParams.set("redirect", redirectTarget);
  }
  if (intent) {
    authSearchParams.set("intent", intent);
  }
  redirect(`/auth?${authSearchParams.toString()}`);
}
