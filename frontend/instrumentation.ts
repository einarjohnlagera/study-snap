// THROW-AWAY (branch verify/v0179-cache only): installs the preview stub on the Vercel preview of this branch.
export async function register() {
  if (process.env.NEXT_RUNTIME !== "nodejs") return;
  const enabled = process.env.PREVIEW_VERIFY === "1" || process.env.VERCEL_GIT_COMMIT_REF === "verify/v0179-cache";
  if (!enabled) return;
  const { installPreviewStub } = await import("./preview-verify/install");
  installPreviewStub();
}
